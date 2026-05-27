package th.ac.bodin2.electives.api.routes

import io.ktor.resources.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.resources.*
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.RATE_LIMIT_USERS
import th.ac.bodin2.electives.api.RATE_LIMIT_USERS_SELECTIONS
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.EnrollmentSelectionService
import th.ac.bodin2.electives.api.services.EnrollmentSelectionService.*
import th.ac.bodin2.electives.api.services.SubjectService
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.proto.api.UsersService.SetStudentEnrollmentSelectionRequest
import th.ac.bodin2.electives.proto.api.UsersService as UsersProto

val usersController = controller {
    val usersService: UsersService by dependencies
    val enrollmentSelectionService: EnrollmentSelectionService by dependencies
    val subjectService: SubjectService by dependencies

    routing {
        authenticatedRoutes {
            rateLimit(RATE_LIMIT_USERS) {
                get<Users.Id> {
                    resolveUserIdEnforced(it.id) { userId, _ ->
                        context(usersService) { handleGetUser(userId) }
                    }
                }

                context(enrollmentSelectionService) {
                    get<Users.Id.Selections> {
                        resolveUserIdEnforced(it.parent.id) { userId, _ ->
                            handleGetStudentSelections(userId)
                        }
                    }
                }

                context(subjectService) {
                    get<Users.Id.Subjects> {
                        resolveUserIdEnforced(it.parent.id) { userId, _ ->
                            handleGetTeacherSubjects(userId)
                        }
                    }
                }
            }

            rateLimit(RATE_LIMIT_USERS_SELECTIONS) {
                context(enrollmentSelectionService) {
                    put<Users.Id.Selections.EnrollmentId> {
                        resolveUserIdEnforced(it.parent.parent.id) { userId, authenticatedUserId ->
                            handlePutStudentEnrollmentSelection(it.enrollmentId, userId, authenticatedUserId)
                        }
                    }

                    delete<Users.Id.Selections.EnrollmentId> {
                        resolveUserIdEnforced(it.parent.parent.id) { userId, authenticatedUserId ->
                            handleDeleteStudentEnrollmentSelection(it.enrollmentId, userId, authenticatedUserId)
                        }
                    }
                }
            }
        }
    }
}

context(enrollmentSelectionService: EnrollmentSelectionService)
suspend fun RoutingContext.handleGetStudentSelections(userId: Int) {
    val response = try {
        transaction {
            val selections = enrollmentSelectionService.getStudentSelections(userId)

            UsersProto.StudentSelections(
                subjects = selections.mapValues {
                    it.value.toProto(
                        enrollmentId = it.key,
                        withDescription = false,
                        withTeachers = true,
                        withEnrolledCounts = true,
                    )
                }
            )
        }
    } catch (_: EntityNotFoundException) {
        throw badRequest("Viewing selections for non-student users")
    }

    // @TODO: Return more specific error if user is not a student?
    // But that requires an extra query and exposes unnecessary information...

    call.respond(response)
}

context(usersService: UsersService)
suspend fun RoutingContext.handleGetUser(userId: Int) {
    val userProto = transaction {
        try {
            when (val type = usersService.getUserType(userId)) {
                UserType.STUDENT -> usersService.getStudentById(userId)?.toProto()
                UserType.TEACHER -> usersService.getTeacherById(userId)?.toProto()
                UserType.ADMIN -> usersService.getAdminById(userId)?.toProto()
            }
        } catch (_: EntityNotFoundException) {
            null
        }
    } ?: throw userNotFound()

    call.respond(userProto)
}

context(subjectService: SubjectService)
suspend fun RoutingContext.handleGetTeacherSubjects(userId: Int) {
    val response = try {
        transaction {
            UsersProto.TeacherSubjects(
                subjects = subjectService.getTeacherSubjects(userId).mapValues {
                    it.value.toProto(
                        enrollmentId = it.key,
                        withDescription = false,
                        withTeachers = false,
                    )
                }
            )
        }
    } catch (_: EntityNotFoundException) {
        throw badRequest("Viewing subjects for non-teacher users")
    }

    call.respond(response)
}

context(enrollmentSelectionService: EnrollmentSelectionService)
private suspend fun RoutingContext.handlePutStudentEnrollmentSelection(
    enrollmentId: Int,
    studentId: Int,
    executor: UsersService.SessionUser
) {
    val req = call.parseOrNull<SetStudentEnrollmentSelectionRequest>() ?: throw badRequest()

    @OptIn(Transactional::class)
    when (val result =
        enrollmentSelectionService.setStudentSelection(executor, studentId, enrollmentId, req.subject_id)) {
        ModifySelectionResult.Success -> noContent()

        is ModifySelectionResult.NotFound -> {
            /**
             * See [th.ac.bodin2.electives.api.services.EnrollmentSelectionServiceImpl.tryHandling]
             */
            throw when (result.entity) {
                ExceptionEntity.ENROLLMENT -> notFound("Enrollment not found")
                ExceptionEntity.SUBJECT -> badRequest("Subject does not exist")
                ExceptionEntity.STUDENT -> modifyingNonStudentUserSelection()

                else -> IllegalStateException("Unreachable case: ${result.entity}")
            }
        }

        is ModifySelectionResult.CannotModify -> {
            when (result.status) {
                ModifySelectionStatus.FORBIDDEN -> throw forbidden("Not allowed")

                else -> throw IllegalStateException("Unreachable case: ${result.status}")
            }
        }

        is ModifySelectionResult.CannotEnroll -> when (result.status) {
            CanEnrollStatus.SUBJECT_NOT_IN_ENROLLMENT ->
                throw badRequest("Subject is not part of this enrollment")

            CanEnrollStatus.ALREADY_ENROLLED ->
                throw conflict("Student has already enrolled for this enrollment")

            CanEnrollStatus.NOT_IN_ENROLLMENT_GROUP,
            CanEnrollStatus.NOT_IN_SUBJECT_GROUP ->
                throw forbidden("Student does not pass the group requirements")

            CanEnrollStatus.SUBJECT_FULL ->
                throw badRequest("Selected subject is full")

            CanEnrollStatus.NOT_IN_ENROLLMENT_DATE_RANGE ->
                throw badRequest("Not in enrollment date range")

            else -> throw IllegalStateException("Unreachable case: ${result.status}")
        }
    }
}

context(enrollmentSelectionService: EnrollmentSelectionService)
private suspend fun RoutingContext.handleDeleteStudentEnrollmentSelection(
    enrollmentId: Int,
    studentId: Int,
    executor: UsersService.SessionUser
) {
    @OptIn(Transactional::class)
    when (val result = enrollmentSelectionService.deleteStudentSelection(executor, studentId, enrollmentId)) {
        ModifySelectionResult.Success -> noContent()

        is ModifySelectionResult.CannotModify -> {
            throw when (result.status) {
                ModifySelectionStatus.FORBIDDEN -> forbidden("Not allowed")
                ModifySelectionStatus.NOT_ENROLLED -> badRequest("Student has not enrolled in the selected enrollment")
            }
        }

        is ModifySelectionResult.NotFound -> {
            throw when (result.entity) {
                ExceptionEntity.ENROLLMENT -> notFound("Enrollment not found")
                ExceptionEntity.STUDENT -> modifyingNonStudentUserSelection()

                else -> IllegalStateException("Unreachable case: ${result.entity}")
            }
        }

        is ModifySelectionResult.CannotEnroll -> {
            throw when (result.status) {
                EnrollmentSelectionService.CanEnrollStatus.NOT_IN_ENROLLMENT_DATE_RANGE ->
                    badRequest("Not in enrollment date range")

                else -> IllegalStateException("Unreachable case: ${result.status}")
            }
        }
    }
}


private const val ME_USER_ID = "@me"

/**
 * Resolve user IDs for routes that accept either a specific user ID or "@me" to refer to the authenticated user.
 *
 * Only teachers can access other users' data; students can only access their own data.
 */
private suspend inline fun RoutingContext.resolveUserIdEnforced(
    idParam: String,
    crossinline block: suspend (gettingUserId: Int, executor: UsersService.SessionUser) -> Unit
) {
    authenticated({ userId ->
        // If accessing own data, allow all user types
        idParam.toIntOrNull()?.let {
            if (it == userId) {
                return@authenticated ALL_USER_TYPES
            }
        }

        if (idParam == ME_USER_ID) ALL_USER_TYPES else ELEVATED_USER_ONLY
    }) { user ->
        val gettingUserId = if (idParam == ME_USER_ID) {
            user.id
        } else {
            idParam.toIntOrNull() ?: throw badRequest()
        }

        block(gettingUserId, user)
    }
}

private fun userNotFound() = notFound("User not found")
private fun modifyingNonStudentUserSelection() =
    badRequest("Modifying selections for non-student users")

@Suppress("UNUSED")
@Resource("/users")
private class Users {
    @Resource("{id}")
    class Id(val parent: Users = Users(), val id: String) {
        @Resource("selections")
        class Selections(val parent: Id) {
            @Resource("{enrollmentId}")
            class EnrollmentId(val parent: Selections, val enrollmentId: Int)
        }

        @Resource("subjects")
        class Subjects(val parent: Id)
    }
}
