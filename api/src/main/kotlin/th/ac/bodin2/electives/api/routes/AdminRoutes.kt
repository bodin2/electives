package th.ac.bodin2.electives.api.routes

import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.api.ADMIN_AUTHENTICATION
import th.ac.bodin2.electives.api.services.AdminService
import th.ac.bodin2.electives.api.services.AdminService.CreateSessionResult
import th.ac.bodin2.electives.api.services.NotificationsService
import th.ac.bodin2.electives.api.utils.connectingAddress
import th.ac.bodin2.electives.api.utils.notFound
import th.ac.bodin2.electives.api.utils.parseOrNull
import th.ac.bodin2.electives.api.utils.respond
import th.ac.bodin2.electives.api.utils.unauthorized
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.models.Students
import th.ac.bodin2.electives.db.models.Teachers
import th.ac.bodin2.electives.db.models.Users
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.proto.api.AdminServiceKt.challengeResponse
import th.ac.bodin2.electives.proto.api.AdminServiceKt.listUsersResponse
import th.ac.bodin2.electives.proto.api.AuthService
import th.ac.bodin2.electives.proto.api.AuthServiceKt.authenticateResponse

fun Application.registerAdminRoutes() {
    val adminService: AdminService by dependencies
    val notificationsService: NotificationsService by dependencies

    AdminController(adminService, notificationsService).apply { register() }
}

class AdminController(private val adminService: AdminService, private val notificationsService: NotificationsService) {
    companion object {
        private const val PAGE_SIZE = 50
    }

    fun Application.register() {
        routing {
            authenticate(ADMIN_AUTHENTICATION) {
                get<Admin.Users.Students> { params ->
                    handleGetUsers(Students, Student::from, Student::toProto, params.page)
                }

                get<Admin.Users.Teachers> { params ->
                    handleGetUsers(Teachers, Teacher::from, Teacher::toProto, params.page)
                }
            }

            get<Admin.Challenge> {
                call.respond(challengeResponse {
                    challenge = adminService.newChallenge()
                })
            }

            post<Admin.Auth> {
                val req = call.parseOrNull<AuthService.AuthenticateRequest>() ?: return@post notFound()

                try {
                    when (val result = adminService.createSession(req.password, call.request.connectingAddress)) {
                        is CreateSessionResult.Success -> {
                            call.respond(authenticateResponse {
                                token = result.token
                            })
                        }

                        is CreateSessionResult.NoChallenge,
                        is CreateSessionResult.IPNotAllowed -> notFound()

                        is CreateSessionResult.InvalidSignature -> unauthorized()
                    }

                } catch (e: Exception) {
                    log.error("Attempt create admin session failed: ${e.message}")
                    unauthorized()
                }
            }

            resource<Admin.Notifications> {
                webSocket {
                    notificationsService.apply { handleAdminConnection() }
                }
            }
        }
    }

    private suspend fun <T> RoutingContext.handleGetUsers(
        table: IdTable<Int>,
        transformer: (row: ResultRow) -> T,
        protoTransformer: T.() -> th.ac.bodin2.electives.proto.api.User,
        page: Int
    ) {
        val offset = ((page - 1) * PAGE_SIZE).toLong()
        val list = transaction {
            table
                .select(
                    table.columns + listOf(
                        Users.id,
                        Users.avatarUrl,
                        Users.firstName,
                        Users.middleName,
                        Users.lastName
                    )
                )
                .limit(PAGE_SIZE)
                .offset(offset)
                .map { transformer(it) }
        }

        call.respond(listUsersResponse {
            users += list.map { it.protoTransformer() }
        })
    }

    @Suppress("UNUSED")
    @Resource("/admin")
    class Admin {
        @Resource("challenge")
        class Challenge(val parent: Admin)

        @Resource("auth")
        class Auth(val parent: Admin)

        @Resource("notifications")
        class Notifications(val parent: Admin)

        @Resource("users")
        class Users(val parent: Admin) {
            // GET: ListUsersResponse
            @Resource("students")
            class Students(val parent: Users, val page: Int = 1)

            // GET: ListUsersResponse
            @Resource("teachers")
            class Teachers(val parent: Users, val page: Int = 1)

            // PUT: User, GET: User, DELETE, PATCH: UserPatch
            @Resource("{id}")
            class Id(val parent: Users, val id: Int) {
                // GET: UsersService.StudentSelections, PUT: UsersService.StudentSelections
                @Resource("selections")
                class Selections(val parent: Id)
            }
        }

        // GET: ElectivesService.ListResponse
        @Resource("electives")
        class Electives(val parent: Admin) {
            // PUT: Elective, GET: Elective, DELETE, PATCH: ElectivePatch
            @Resource("{id}")
            class Id(val parent: Electives, val id: Int) {
                // GET, PUT
                @Resource("subjects")
                class Subjects(val parent: Id)
            }
        }

        // GET: ElectivesService.ListSubjectsResponse
        @Resource("subjects")
        class Subjects(val parent: Admin) {
            // PUT: Subject, GET: Subject, DELETE, PATCH: SubjectPatch
            @Resource("{id}")
            class Id(val parent: Electives, val id: Int)
        }

        // GET: ListTeamsResponse
        @Resource("teams")
        class Teams(val parent: Admin) {
            // PUT: Team, GET: Team, DELETE, PATCH: TeamPatch
            @Resource("{id}")
            class Id(val parent: Teams, val id: Int)
        }
    }
}