package th.ac.bodin2.electives.api.routes

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.resources.*
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.routing
import org.jetbrains.exposed.sql.transactions.transaction
import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.api.services.ElectiveSelectionService
import th.ac.bodin2.electives.api.services.ElectiveSelectionService.ModifySelectionResult
import th.ac.bodin2.electives.api.services.ElectiveSelectionService.ModifySelectionStatus
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.proto.api.UsersService.SetStudentElectiveSelectionRequest
import th.ac.bodin2.electives.proto.api.UsersServiceKt.getStudentSelectionsResponse

fun Application.registerUsersRoutes() {
    val usersService: UsersService by dependencies
    val electiveSelectionService: ElectiveSelectionService by dependencies

    UsersController(usersService, electiveSelectionService).apply { register() }
}

class UsersController(
    private val usersService: UsersService,
    private val electiveSelectionService: ElectiveSelectionService
) {
    fun Application.register() {
        routing {
            authenticatedRoutes {
                get<Users.Id> {
                    resolveUserIdEnforced(it.id) { userId, _ ->
                        handleGetUser(userId)
                    }
                }

                get<Users.Id.Selections> {
                    resolveUserIdEnforced(it.parent.id) { userId, _ ->
                        handleGetStudentSelections(userId)
                    }
                }

                put<Users.Id.Selections.ElectiveId> {
                    resolveUserIdEnforced(it.parent.parent.id) { userId, authenticatedUserId ->
                        handlePutStudentElectiveSelection(it.electiveId, userId, authenticatedUserId)
                    }
                }

                delete<Users.Id.Selections.ElectiveId> {
                    resolveUserIdEnforced(it.parent.parent.id) { userId, authenticatedUserId ->
                        handleDeleteStudentElectiveSelection(it.electiveId, userId, authenticatedUserId)
                    }
                }
            }
        }
    }

    private suspend fun RoutingContext.handleGetUser(userId: Int) {
        val userProto = transaction {
            try {
                when (val type = usersService.getUserType(userId)) {
                    UserType.STUDENT -> usersService.getStudentById(userId)?.toProto() ?: return@transaction null
                    UserType.TEACHER -> usersService.getTeacherById(userId)?.toProto() ?: return@transaction null

                    else -> throw IllegalStateException("Unknown user type: $type (id: $userId)")
                }
            } catch (_: NotFoundException) {
                return@transaction null
            }
        } ?: return userNotFoundError()

        call.respond(userProto)
    }

    private suspend fun RoutingContext.handleGetStudentSelections(userId: Int) {
        val response = transaction {
            val student = usersService.getStudentById(userId) ?: return@transaction null

            getStudentSelectionsResponse {
                student.selections.map { (elective, subject) ->
                    subjects.put(elective.id.value, subject.toProto())
                }
            }
        } ?: return badRequest("Viewing selections for non-student users")

        call.respond(response)
    }

    private suspend fun RoutingContext.handlePutStudentElectiveSelection(
        electiveId: Int,
        userId: Int,
        authenticatedUserId: Int
    ) {
        val req = call.parseOrNull<SetStudentElectiveSelectionRequest>() ?: return badRequest()

        @OptIn(CreatesTransaction::class)
        when (val result =
            electiveSelectionService.setStudentSelection(authenticatedUserId, userId, electiveId, req.subjectId)) {
            ModifySelectionResult.Success -> call.response.status(HttpStatusCode.OK)

            is ModifySelectionResult.NotFound -> {
                /**
                 * See [th.ac.bodin2.electives.api.services.ElectiveSelectionServiceImpl.tryHandling]
                 */
                return when (result.entity) {
                    NotFoundEntity.ELECTIVE -> notFound("Elective not found")
                    NotFoundEntity.SUBJECT -> badRequest("Subject does not exist")
                    NotFoundEntity.STUDENT -> modifyingNonStudentUserSelectionError()

                    else -> throw IllegalStateException("Unreachable case: ${result.entity}")
                }
            }

            is ModifySelectionResult.CannotModify -> {
                when (result.status) {
                    ModifySelectionStatus.FORBIDDEN -> return forbidden("Not allowed")

                    else -> throw IllegalStateException("Unreachable case: ${result.status}")
                }
            }

            is ModifySelectionResult.CannotEnroll -> when (result.status) {
                ElectiveSelectionService.CanEnrollStatus.SUBJECT_NOT_IN_ELECTIVE ->
                    return badRequest("Subject is not part of this elective")

                ElectiveSelectionService.CanEnrollStatus.ALREADY_ENROLLED ->
                    return conflict("Student has already enrolled for this elective")

                ElectiveSelectionService.CanEnrollStatus.NOT_IN_ELECTIVE_TEAM,
                ElectiveSelectionService.CanEnrollStatus.NOT_IN_SUBJECT_TEAM ->
                    return forbidden("Student does not pass the team requirements")

                ElectiveSelectionService.CanEnrollStatus.SUBJECT_FULL ->
                    return badRequest("Selected subject is full")

                ElectiveSelectionService.CanEnrollStatus.NOT_IN_ELECTIVE_DATE_RANGE ->
                    return badRequest("Not in elective enrollment date range")

                else -> throw IllegalStateException("Unreachable case: ${result.status}")
            }
        }
    }

    private suspend fun RoutingContext.handleDeleteStudentElectiveSelection(
        electiveId: Int,
        userId: Int,
        authenticatedUserId: Int
    ) {
        @OptIn(CreatesTransaction::class)
        when (val result = electiveSelectionService.deleteStudentSelection(authenticatedUserId, userId, electiveId)) {
            ModifySelectionResult.Success -> call.response.status(HttpStatusCode.OK)

            is ModifySelectionResult.CannotModify -> {
                return when (result.status) {
                    ModifySelectionStatus.FORBIDDEN -> forbidden("Not allowed")
                    ModifySelectionStatus.NOT_ENROLLED -> badRequest("Student has not enrolled in the selected elective")
                }
            }

            is ModifySelectionResult.NotFound -> {
                return when (result.entity) {
                    NotFoundEntity.ELECTIVE -> notFound("Elective not found")
                    NotFoundEntity.STUDENT -> modifyingNonStudentUserSelectionError()

                    else -> throw IllegalStateException("Unreachable case: ${result.entity}")
                }
            }

            else -> throw IllegalStateException("Reached unreachable case: $result")
        }
    }

    @Suppress("UNUSED")
    @Resource("/users")
    class Users {
        @Resource("{id}")
        class Id(val parent: Users = Users(), val id: String) {
            @Resource("selections")
            class Selections(val parent: Id) {
                @Resource("{electiveId}")
                class ElectiveId(val parent: Selections, val electiveId: Int)
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
    crossinline block: suspend (gettingUserId: Int, authenticatedUserId: Int) -> Unit
) {
    authenticated({ userId ->
        // If accessing own data, allow all user types
        idParam.toIntOrNull()?.let {
            if (it == userId) {
                return@authenticated ALL_USER_TYPES
            }
        }

        if (idParam == ME_USER_ID) ALL_USER_TYPES else TEACHER_USER_ONLY
    }) { userId ->
        val gettingUserId = if (idParam == ME_USER_ID) {
            userId
        } else {
            idParam.toIntOrNull() ?: return@authenticated badRequest()
        }

        block(gettingUserId, userId)
    }
}

private suspend inline fun RoutingContext.userNotFoundError() = notFound("User not found")
private suspend inline fun RoutingContext.modifyingNonStudentUserSelectionError() =
    badRequest("Modifying selections for non-student users")