package th.ac.bodin2.electives.api.routes

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.routing
import org.jetbrains.exposed.sql.transactions.transaction
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.proto.api.UsersService.SetStudentElectiveSelectionRequest
import th.ac.bodin2.electives.proto.api.UsersServiceKt.getStudentSelectionsResponse

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

private const val ME_USER_ID = "@me"

private suspend fun RoutingContext.handleGetUser(userId: Int) {
    try {
        when (val type = UsersService.getUserType(userId)) {
            UserType.STUDENT -> {
                val student = UsersService.getStudentById(userId) ?: return userNotFoundError()

                call.respond(student.toProto())
            }

            UserType.TEACHER -> {
                val teacher = UsersService.getTeacherById(userId) ?: return userNotFoundError()

                call.respond(teacher.toProto())
            }

            else -> {
                throw IllegalStateException("Unknown user type: $type (id: $userId)")
            }
        }
    } catch (_: NotFoundException) {
        return userNotFoundError()
    }
}

private suspend fun RoutingContext.handleGetStudentSelections(userId: Int) {
    if (UsersService.getUserType(userId) != UserType.STUDENT) {
        return badRequest("Viewing selections for non-student users")
    }

    val student = UsersService.getStudentById(userId)!!

    call.respond(getStudentSelectionsResponse {
        transaction {
            student.selections.map { (elective, subject) ->
                subjects.put(elective.id.value, subject.toProto())
            }
        }
    })
}

private suspend fun RoutingContext.handlePutStudentElectiveSelection(
    electiveId: Int,
    userId: Int,
    authenticatedUserId: Int
) {
    if (UsersService.getUserType(userId) != UserType.STUDENT) {
        return badRequest("Modifying selections for non-student users")
    }

    val req = call.parseOrNull<SetStudentElectiveSelectionRequest>() ?: return badRequest("Invalid request body")
    if (
        UsersService.getUserType(authenticatedUserId) == UserType.TEACHER &&
        !Teacher.teachesSubject(authenticatedUserId, req.subjectId)
    ) {
        return teacherDoesNotTeachSubjectError()
    }

    when (Student.canEnrollInSubject(userId, electiveId, req.subjectId)) {
        Student.CanEnrollStatus.CAN_ENROLL -> {}
        Student.CanEnrollStatus.SUBJECT_NOT_IN_ELECTIVE -> return badRequest("Subject is not part of this elective")

        Student.CanEnrollStatus.NOT_IN_SUBJECT_TEAM,
        Student.CanEnrollStatus.NOT_IN_ELECTIVE_TEAM -> return forbidden("Student is not part of the team")

        Student.CanEnrollStatus.ALREADY_ENROLLED -> return conflict("Student is already enrolled in this elective")
        Student.CanEnrollStatus.SUBJECT_FULL -> return conflict("Subject is full")
    }

    Student.setElectiveSelection(userId, electiveId, req.subjectId)

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

    val subjectId = Student.getElectiveSelectionId(userId, electiveId)
        ?: return badRequest("Student has not enrolled in the selected elective")

    if (UsersService.getUserType(authenticatedUserId) == UserType.TEACHER &&
        !Teacher.teachesSubject(authenticatedUserId, subjectId)
    ) {
        return teacherDoesNotTeachSubjectError()
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

private suspend inline fun RoutingContext.userNotFoundError() = notFound("User not found")
private suspend inline fun RoutingContext.teacherDoesNotTeachSubjectError() =
    forbidden("Teacher does not teach the selected subject")

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