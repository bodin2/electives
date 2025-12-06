package th.ac.bodin2.electives.api.routes

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.routing.*
import io.ktor.server.routing.put
import org.jetbrains.exposed.sql.transactions.transaction
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.proto.api.UsersService.GetStudentSelectionsResponse
import th.ac.bodin2.electives.proto.api.UsersService.SetStudentElectiveSelectionRequest

fun Application.registerUsersRoutes() {
    routing {
        authenticated {
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

private const val ME_USER_ID = "@me"

private suspend fun RoutingContext.handleGetUser(userId: Int) {
    when (val type = UsersService.getUserType(userId)) {
        UserType.STUDENT -> {
            val student = UsersService.getStudentById(userId)
                ?: return userNotFoundError()

            call.respondMessage(student.toProto())
        }

        UserType.TEACHER -> {
            val teacher = UsersService.getTeacherById(userId)
                ?: return userNotFoundError()

            call.respondMessage(teacher.toProto())
        }

        else -> {
            throw IllegalStateException("Unknown user type: $type (id: $userId)")
        }
    }
}

private suspend fun RoutingContext.handleGetStudentSelections(userId: Int) {
    if (UsersService.getUserType(userId) != UserType.STUDENT) {
        return badRequest("Viewing selections for non-student users")
    }

    val student = UsersService.getStudentById(userId)!!

    call.respondMessage(GetStudentSelectionsResponse.newBuilder().apply {
        transaction {
            student.selections.map { (elective, subject) ->
                putSubjects(elective.id.value, subject.toProto())
            }
        }
    }.build())
}

private suspend fun RoutingContext.handlePutStudentElectiveSelection(
    electiveId: Int,
    userId: Int,
    authenticatedUserId: Int
) {
    if (UsersService.getUserType(userId) != UserType.STUDENT) {
        return badRequest("Modifying selections for non-student users")
    }

    val req = call.parse<SetStudentElectiveSelectionRequest>()
    if (
        UsersService.getUserType(authenticatedUserId) == UserType.TEACHER &&
        !Teacher.teachesSubject(authenticatedUserId, req.subjectId)
    ) {
        return teacherDoesNotTeachSubjectError()
    }

    val student = UsersService.getStudentById(userId)
        ?: return userNotFoundError()

    student.setElectiveSelection(electiveId, req.subjectId)

    call.response.status(HttpStatusCode.OK)
}

private suspend fun RoutingContext.handleDeleteStudentElectiveSelection(
    electiveId: Int,
    userId: Int,
    authenticatedUserId: Int
) {
    if (UsersService.getUserType(userId) != UserType.STUDENT) {
        return badRequest("Modifying selections for non-student users")
    }

    if (UsersService.getUserType(authenticatedUserId) == UserType.TEACHER) {
        val subjectId = Student.getElectiveSelectionId(userId, electiveId)
            ?: return badRequest("Student has not enrolled in the selected elective")

        if (!Teacher.teachesSubject(authenticatedUserId, subjectId)) {
            return teacherDoesNotTeachSubjectError()
        }
    }

    // This will never throw, because we already checked that the selection exists
    Student.removeElectiveSelection(userId, electiveId)

    call.response.status(HttpStatusCode.OK)
}

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
            idParam.toIntOrNull() ?: return@authenticated userNotFoundError()
        }

        block(gettingUserId, userId)
    }
}

private suspend inline fun RoutingContext.userNotFoundError() = badRequest("User not found")
private suspend inline fun RoutingContext.teacherDoesNotTeachSubjectError() =
    badRequest("Teacher does not teach the selected subject")

@Suppress("UNUSED")
@Resource("/users")
private class Users {
    @Resource("{id}")
    class Id(val parent: Users = Users(), val id: String) {
        @Resource("selections")
        class Selections(val parent: Id) {
            @Resource("{electiveId}")
            class ElectiveId(val parent: Selections, val electiveId: Int)
        }
    }
}