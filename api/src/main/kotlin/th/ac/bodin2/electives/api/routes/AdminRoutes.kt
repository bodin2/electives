package th.ac.bodin2.electives.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import th.ac.bodin2.electives.NotFoundException
import io.ktor.server.plugins.di.*
import io.ktor.server.resources.*
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.routing
import io.ktor.server.websocket.*
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.api.ADMIN_AUTHENTICATION
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.api.services.AdminService
import th.ac.bodin2.electives.api.services.AdminService.CreateSessionResult
import th.ac.bodin2.electives.api.services.ElectiveSelectionService
import th.ac.bodin2.electives.api.services.NotificationsService
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.badRequest
import th.ac.bodin2.electives.api.utils.connectingAddress
import th.ac.bodin2.electives.api.utils.notFound
import th.ac.bodin2.electives.api.utils.ok
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
import th.ac.bodin2.electives.proto.api.UserType

fun Application.registerAdminRoutes() {
    val adminService: AdminService by dependencies
    val notificationsService: NotificationsService by dependencies
    val usersService: UsersService by dependencies
    val electiveSelectionService: ElectiveSelectionService by dependencies

    AdminController(adminService, notificationsService, usersService, electiveSelectionService).apply { register() }
}

class AdminController(
    private val adminService: AdminService,
    private val notificationsService: NotificationsService,
    private val usersService: UsersService,
    private val electiveSelectionService: ElectiveSelectionService
) {
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

                get<Admin.Users.Id> { params ->
                    context(usersService) {
                        handleGetUser(params.id)
                    }
                }

                put<Admin.Users.Id> { params ->
                    val req = call.parseOrNull<th.ac.bodin2.electives.proto.api.AdminService.AddUserRequest>()
                        ?: return@put badRequest()
                    val user = req.user
                    if (user.id != params.id) return@put badRequest("ID in URL does not match body")

                    val userOrNull = transaction {
                        when (user.type) {
                            UserType.STUDENT -> usersService.createStudent(
                                id = user.id,
                                firstName = user.firstName,
                                middleName = user.middleName,
                                lastName = user.lastName,
                                password = req.password,
                                avatarUrl = user.avatarUrl
                            )

                            UserType.TEACHER -> usersService.createTeacher(
                                id = user.id,
                                firstName = user.firstName,
                                middleName = user.middleName,
                                lastName = user.lastName,
                                password = req.password,
                                avatarUrl = user.avatarUrl
                            )

                            else -> null
                        }
                    }

                    when (userOrNull) {
                        is Student -> call.respond(userOrNull.toProto())
                        is Teacher -> call.respond(userOrNull.toProto())

                        else -> badRequest()
                    }
                }

                patch<Admin.Users.Id> { params ->
                    val req = call.parseOrNull<th.ac.bodin2.electives.proto.api.AdminService.UserPatch>()
                        ?: return@patch badRequest()

                    val update = UsersService.UserUpdate(
                        firstName = if (req.hasFirstName()) req.firstName else null,
                        middleName = if (req.hasMiddleName()) req.middleName else null,
                        lastName = if (req.hasLastName()) req.lastName else null,
                        avatarUrl = if (req.hasAvatarUrl()) req.avatarUrl else null,
                        setMiddleName = req.patchMiddleName,
                        setAvatarUrl = req.patchAvatarUrl,
                    )

                    try {
                        @OptIn(CreatesTransaction::class)
                        when (val type = usersService.getUserType(params.id)) {
                            UserType.STUDENT -> {
                                usersService.updateStudent(
                                    params.id,
                                    UsersService.StudentUpdate(update)
                                )
                            }

                            UserType.TEACHER -> {
                                usersService.updateTeacher(
                                    params.id,
                                    UsersService.TeacherUpdate(update)
                                )
                            }

                            else -> throw IllegalStateException("Unreachable case: $type")
                        }

                        if (req.hasNewPassword()) {
                            @OptIn(CreatesTransaction::class)
                            usersService.setPassword(params.id, req.newPassword)
                        }

                        ok()
                    } catch (e: NotFoundException) {
                        when (e.entity) {
                            NotFoundEntity.USER,
                            NotFoundEntity.TEACHER,
                            NotFoundEntity.STUDENT -> return@patch badRequest("User not found")

                            NotFoundEntity.TEAM -> return@patch badRequest("One or more teams not found")

                            else -> throw e
                        }
                    }
                }

                delete<Admin.Users.Id> { params ->
                    try {
                        @OptIn(CreatesTransaction::class)
                        usersService.deleteUser(params.id)
                        ok()
                    } catch (_: NotFoundException) {
                        return@delete badRequest("User not found")
                    }
                }

                get<Admin.Users.Id.Selections> { params ->
                    context(electiveSelectionService) {
                        handleGetStudentSelections(params.parent.id)
                    }
                }

                put<Admin.Users.Id.Selections> { params ->
                    val req = call.parseOrNull<th.ac.bodin2.electives.proto.api.AdminService.SetStudentSelectionsRequest>()
                        ?: return@put badRequest()

                    try {
                        @OptIn(CreatesTransaction::class)
                        electiveSelectionService.forceSetAllStudentSelections(params.parent.id, req.selectionsMap)

                        ok()
                    } catch (e: NotFoundException) {
                        when (e.entity) {
                            NotFoundEntity.STUDENT -> return@put badRequest("Student not found")
                            NotFoundEntity.ELECTIVE -> return@put badRequest("One or more electives not found")
                            NotFoundEntity.SUBJECT -> return@put badRequest("One or more subjects not found")

                            else -> throw e
                        }
                    }
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

            // GET: User, DELETE, PATCH: UserPatch
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