package th.ac.bodin2.electives.api.routes

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.routing
import org.jetbrains.exposed.sql.transactions.transaction
import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.services.NotificationsService
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.db.*
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.proto.api.UsersService.SetStudentElectiveSelectionRequest
import th.ac.bodin2.electives.proto.api.UsersServiceKt.getStudentSelectionsResponse
import th.ac.bodin2.electives.utils.isTest
import java.sql.Connection.TRANSACTION_SERIALIZABLE

fun Application.registerUsersRoutes() {
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

private suspend fun RoutingContext.handleGetUser(userId: Int) {
    val userProto = transaction {
        try {
            when (val type = UsersService.getUserType(userId)) {
                UserType.STUDENT -> UsersService.getStudentById(userId)?.toProto() ?: return@transaction null
                UserType.TEACHER -> UsersService.getTeacherById(userId)?.toProto() ?: return@transaction null

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
        val student = UsersService.getStudentById(userId) ?: return@transaction null

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

    // We're currently using SQLite, but in case if we ever switch, adding this isolation will be required
    // We want to prevent overbooking: Thread A reads (29/30) -> Thread B reads (29/30) -> A inserts (30/30) -> B inserts (31/30)
    // SERIALIZABLE will ensure that if two transactions try to do this at the same time, one of them will be fail and be retried by Exposed
    val result: SelectionRequestResult = transaction(TRANSACTION_SERIALIZABLE) {
        maxAttempts = 3

        try {
            val student = Student.require(userId)
            val elective = Elective.require(electiveId)
            val subject = Subject.require(req.subjectId)

            // Acquire lock (will block instances of this transaction running in parallel)
            if (!isTest) exec("BEGIN IMMEDIATE")

            when (Student.canEnrollInSubject(student, elective, subject)) {
                Student.CanEnrollStatus.CAN_ENROLL -> {}
                Student.CanEnrollStatus.SUBJECT_NOT_IN_ELECTIVE -> return@transaction SelectionRequestResult.SUBJECT_NOT_IN_ELECTIVE

                Student.CanEnrollStatus.NOT_IN_SUBJECT_TEAM,
                Student.CanEnrollStatus.NOT_IN_ELECTIVE_TEAM -> return@transaction SelectionRequestResult.NOT_IN_TEAM

                Student.CanEnrollStatus.ALREADY_ENROLLED -> return@transaction SelectionRequestResult.ALREADY_ENROLLED

                Student.CanEnrollStatus.SUBJECT_FULL -> return@transaction SelectionRequestResult.SUBJECT_FULL
            }

            if (UsersService.getUserType(authenticatedUserId) == UserType.TEACHER) {
                val teacher = Teacher.require(authenticatedUserId)
                if (!Teacher.teachesSubject(teacher, req.subjectId)) {
                    return@transaction SelectionRequestResult.TEACHER_DOES_NOT_TEACH_SUBJECT
                }
            }

            Student.setElectiveSelection(student, elective, subject)
            NotificationsService.notifySubjectSelectionUpdate(
                electiveId,
                req.subjectId,
                Subject.getEnrolledCount(subject, elective)
            )

            SelectionRequestResult.SUCCESS
        } catch (e: NotFoundException) {
            return@transaction when (e.entity) {
                NotFoundEntity.STUDENT -> SelectionRequestResult.NOT_STUDENT
                NotFoundEntity.SUBJECT -> SelectionRequestResult.SUBJECT_NOT_FOUND
                NotFoundEntity.ELECTIVE -> SelectionRequestResult.ELECTIVE_NOT_FOUND

                else -> throw e
            }
        }
    }

    when (result) {
        SelectionRequestResult.SUCCESS -> call.response.status(HttpStatusCode.OK)

        SelectionRequestResult.NOT_STUDENT -> return modifyingNonStudentUserSelectionError()
        SelectionRequestResult.ELECTIVE_NOT_FOUND -> return notFound("Elective not found")
        SelectionRequestResult.TEACHER_DOES_NOT_TEACH_SUBJECT -> return teacherDoesNotTeachSubjectError()
        SelectionRequestResult.SUBJECT_NOT_FOUND -> return badRequest("Subject does not exist")
        SelectionRequestResult.SUBJECT_NOT_IN_ELECTIVE -> return badRequest("Subject is not part of this elective")
        SelectionRequestResult.NOT_IN_TEAM -> return forbidden("Student does not pass the team requirements")
        SelectionRequestResult.ALREADY_ENROLLED -> return conflict("Student has already enrolled for this elective")
        SelectionRequestResult.SUBJECT_FULL -> return badRequest("Selected subject is full")

        else -> throw IllegalStateException("Reached unreachable case: $result")
    }
}

private suspend fun RoutingContext.handleDeleteStudentElectiveSelection(
    electiveId: Int,
    userId: Int,
    authenticatedUserId: Int
) {
    val result: SelectionRequestResult = transaction {
        try {
            val student = Student.require(userId)
            val elective = Elective.require(electiveId)

            if (UsersService.getUserType(authenticatedUserId) == UserType.TEACHER) {
                val subjectId = Student.getElectiveSelectionId(student, elective)
                    ?: return@transaction SelectionRequestResult.NOT_ENROLLED

                val teacher = Teacher.require(authenticatedUserId)

                if (!Teacher.teachesSubject(teacher, subjectId))
                    return@transaction SelectionRequestResult.TEACHER_DOES_NOT_TEACH_SUBJECT
            }

            val selection = Student.getElectiveSelectionId(student, elective)
                ?: return@transaction SelectionRequestResult.NOT_ENROLLED

            Student.removeElectiveSelection(student, elective)

            NotificationsService.notifySubjectSelectionUpdate(
                electiveId,
                selection.value,
                Subject.getEnrolledCount(selection, elective)
            )

            SelectionRequestResult.SUCCESS
        } catch (e: NotFoundException) {
            return@transaction when (e.entity) {
                NotFoundEntity.STUDENT -> SelectionRequestResult.NOT_STUDENT
                // Should never throw, we already try to get the selection above
//                NotFoundEntity.ELECTIVE_SELECTION -> SelectionRequestResult.NOT_ENROLLED

                else -> throw e
            }
        }
    }

    when (result) {
        SelectionRequestResult.SUCCESS -> call.response.status(HttpStatusCode.OK)

        SelectionRequestResult.NOT_STUDENT -> return modifyingNonStudentUserSelectionError()
        SelectionRequestResult.NOT_ENROLLED -> return badRequest("Student has not enrolled in the selected elective")
        SelectionRequestResult.TEACHER_DOES_NOT_TEACH_SUBJECT -> return teacherDoesNotTeachSubjectError()

        else -> throw IllegalStateException("Reached unreachable case: $result")
    }
}

private suspend inline fun RoutingContext.userNotFoundError() = notFound("User not found")
private suspend inline fun RoutingContext.teacherDoesNotTeachSubjectError() =
    forbidden("Teacher does not teach the selected subject")

private suspend inline fun RoutingContext.modifyingNonStudentUserSelectionError() =
    badRequest("Modifying selections for non-student users")

private enum class SelectionRequestResult {
    SUCCESS,
    NOT_STUDENT,
    ELECTIVE_NOT_FOUND,
    SUBJECT_NOT_FOUND,
    SUBJECT_NOT_IN_ELECTIVE,
    NOT_IN_TEAM,
    ALREADY_ENROLLED,
    SUBJECT_FULL,
    TEACHER_DOES_NOT_TEACH_SUBJECT,
    NOT_ENROLLED,
}