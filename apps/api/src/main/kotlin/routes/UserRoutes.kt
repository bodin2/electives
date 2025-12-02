package th.ac.bodin2.electives.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.resources.get
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import org.jetbrains.exposed.sql.transactions.transaction
import th.ac.bodin2.electives.api.db.toProto
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.ALL_USER_TYPES
import th.ac.bodin2.electives.api.utils.TEACHER_USER_ONLY
import th.ac.bodin2.electives.api.utils.authenticated
import th.ac.bodin2.electives.api.utils.badRequest
import th.ac.bodin2.electives.api.utils.parse
import th.ac.bodin2.electives.api.utils.respondMessage
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.proto.api.UsersService.GetStudentSelectionsResponse
import th.ac.bodin2.electives.proto.api.UsersService.SetStudentElectiveSelectionRequest
fun Application.registerUserRoutes() {
    routing {
        authenticate("auth-user") {
            get<Users.Id> { params ->
                val idParam = params.id

                resolveUserIdEnforced(idParam) { userId ->
                    when (val type = UsersService.getUserType(userId)) {
                        UserType.STUDENT -> {
                            val student = UsersService.getStudentById(userId)
                                ?: return@resolveUserIdEnforced userNotFoundError()

                            call.respondMessage(student.toProto())
                        }

                        UserType.TEACHER -> {
                            val teacher = UsersService.getTeacherById(userId)
                                ?: return@resolveUserIdEnforced userNotFoundError()

                            call.respondMessage(teacher.toProto())
                        }

                        else -> {
                            throw IllegalStateException("Unknown user type: $type (id: $userId)")
                        }
                    }
                }
            }

            get<Users.Id.Selections> { params ->
                val idParam = params.parent.id

                resolveUserIdEnforced(idParam) { userId ->
                    if (UsersService.getUserType(userId) != UserType.STUDENT) {
                        return@resolveUserIdEnforced badRequest("Viewing selections for non-student users")
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
            }

            put<Users.Id.Selections.ElectiveId> { params ->
                val idParam = params.parent.parent.id

                resolveUserIdEnforced(idParam) { userId ->
                    if (UsersService.getUserType(userId) != UserType.STUDENT) {
                        return@resolveUserIdEnforced badRequest("Modifying selections for non-student users")
                    }

                    val req = call.parse<SetStudentElectiveSelectionRequest>()
                    val electiveId = params.electiveId

                    val student = UsersService.getStudentById(userId)!!

                    student.selectElectiveSubject(electiveId, req.subjectId)

                    call.response.status(HttpStatusCode.OK)
                }
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
    crossinline block: suspend (Int) -> Unit
) {
    authenticated(
        if (idParam == ME_USER_ID) ALL_USER_TYPES else TEACHER_USER_ONLY
    ) { userId ->
        val gettingUserId = if (idParam == ME_USER_ID) {
            userId
        } else {
            idParam.toIntOrNull() ?: return@authenticated userNotFoundError()
        }

        block(gettingUserId)
    }
}

private suspend inline fun RoutingContext.userNotFoundError() = badRequest("User not found")

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