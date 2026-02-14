package th.ac.bodin2.electives.api.routes

import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.application
import io.ktor.server.routing.routing
import io.ktor.server.websocket.*
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.ADMIN_AUTHENTICATION
import th.ac.bodin2.electives.api.RATE_LIMIT_ADMIN
import th.ac.bodin2.electives.api.RATE_LIMIT_ADMIN_AUTH
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.api.services.*
import th.ac.bodin2.electives.api.services.AdminAuthService.CreateSessionResult
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.api.utils.unauthorized
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.proto.api.AdminServiceKt.challengeResponse
import th.ac.bodin2.electives.proto.api.AdminServiceKt.listTeamsResponse
import th.ac.bodin2.electives.proto.api.AdminServiceKt.listUsersResponse
import th.ac.bodin2.electives.proto.api.AuthService
import th.ac.bodin2.electives.proto.api.AuthServiceKt.authenticateResponse
import th.ac.bodin2.electives.proto.api.ElectivesServiceKt.listSubjectsResponse
import th.ac.bodin2.electives.proto.api.UserType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun Application.registerAdminRoutes() {
    val adminAuthService: AdminAuthService by dependencies
    val notificationsService: NotificationsService by dependencies
    val usersService: UsersService by dependencies
    val electiveSelectionService: ElectiveSelectionService by dependencies
    val electiveService: ElectiveService by dependencies
    val subjectService: SubjectService by dependencies
    val teamService: TeamService by dependencies

    AdminAuthController(adminAuthService).apply { register() }
    AdminUsersController(usersService).apply { register() }
    AdminUsersSelectionsController(electiveSelectionService).apply { register() }
    AdminElectivesController(electiveService).apply { register() }
    AdminElectivesSubjectsController(electiveService).apply { register() }
    AdminSubjectsController(subjectService).apply { register() }
    AdminTeamsController(teamService).apply { register() }

    routing {
        resource<Admin.Notifications> {
            webSocket {
                notificationsService.apply { handleAdminConnection() }
            }
        }
    }
}

class AdminAuthController(private val adminAuthService: AdminAuthService) {
    fun Application.register() {
        routing {
            rateLimit(RATE_LIMIT_ADMIN_AUTH) {
                get<Admin.Challenge> {
                    call.respond(challengeResponse {
                        challenge = adminAuthService.newChallenge()
                    })
                }

                post<Admin.Auth> {
                    val req = call.parseOrNull<AuthService.AuthenticateRequest>() ?: return@post notFound()

                    try {
                        when (val result =
                            adminAuthService.createSession(req.password, call.request.origin.remoteAddress)) {
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
                        application.log.error("Attempt create admin session failed: ${e.message}")
                        unauthorized()
                    }
                }
            }
        }
    }
}

class AdminUsersController(
    private val usersService: UsersService,
) {
    fun Application.register() {
        adminRoutes {
            get<Admin.Users.Students> { params ->
                call.respond(listUsersResponse {
                    val (students, count) =
                        @OptIn(CreatesTransaction::class)
                        usersService.getStudents(params.page)

                    users += students.map { it.toProto() }
                    total = count.toInt()
                })
            }

            get<Admin.Users.Teachers> { params ->
                call.respond(listUsersResponse {
                    val (teachers, count) =
                        @OptIn(CreatesTransaction::class)
                        usersService.getTeachers(params.page)

                    users += teachers.map { it.toProto() }
                    total = count.toInt()
                })
            }

            get<Admin.Users.Id> { params -> context(usersService) { handleGetUser(params.id) } }

            put<Admin.Users.Id> { params -> handlePutUser(params.id) }

            patch<Admin.Users.Id> { params -> handlePatchUser(params.id) }

            delete<Admin.Users.Id> { params -> handleDeleteUser(params.id) }
        }
    }

    private suspend fun RoutingContext.handlePutUser(id: Int) {
        val req = call.parseOrNull<th.ac.bodin2.electives.proto.api.AdminService.AddUserRequest>()
            ?: return badRequest()
        val user = req.user
        if (user.id != id) return badRequest("ID in URL does not match body")

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

    private suspend fun RoutingContext.handlePatchUser(id: Int) {
        val req = call.parseOrNull<th.ac.bodin2.electives.proto.api.AdminService.UserPatch>()
            ?: return badRequest()

        try {
            transaction {
                val type = usersService.getUserType(id)

                val update = UsersService.UserUpdate(
                    firstName = if (req.hasFirstName()) req.firstName else null,
                    middleName = if (req.hasMiddleName()) req.middleName else null,
                    lastName = if (req.hasLastName()) req.lastName else null,
                    avatarUrl = if (req.hasAvatarUrl()) req.avatarUrl else null,
                    setMiddleName = req.patchMiddleName,
                    setAvatarUrl = req.patchAvatarUrl,
                )

                @OptIn(CreatesTransaction::class)
                when (type) {
                    UserType.STUDENT -> usersService.updateStudent(
                        id,
                        UsersService.StudentUpdate(
                            update,
                            teams = if (req.patchTeams) req.teamsList else null
                        )
                    )

                    UserType.TEACHER -> usersService.updateTeacher(id, UsersService.TeacherUpdate(update))

                    else -> throw IllegalStateException("Unreachable case: $type")
                }

                if (req.hasNewPassword()) {
                    @OptIn(CreatesTransaction::class)
                    usersService.setPassword(id, req.newPassword)
                }
            }

            ok()
        } catch (e: NotFoundException) {
            return when (e.entity) {
                NotFoundEntity.USER,
                NotFoundEntity.TEACHER,
                NotFoundEntity.STUDENT -> badRequest("User not found")

                NotFoundEntity.TEAM -> badRequest("One or more teams not found")

                else -> throw e
            }
        }
    }

    private suspend fun RoutingContext.handleDeleteUser(id: Int) {
        try {
            @OptIn(CreatesTransaction::class)
            usersService.deleteUser(id)
            ok()
        } catch (_: NotFoundException) {
            return badRequest("User not found")
        }
    }
}

class AdminUsersSelectionsController(
    private val electiveSelectionService: ElectiveSelectionService,
) {
    fun Application.register() {
        adminRoutes {
            get<Admin.Users.Id.Selections> { params ->
                context(electiveSelectionService) {
                    handleGetStudentSelections(params.parent.id)
                }
            }

            put<Admin.Users.Id.Selections> { params -> handlePutStudentSelections(params.parent.id) }
        }
    }

    private suspend fun RoutingContext.handlePutStudentSelections(id: Int) {
        val req = call.parseOrNull<th.ac.bodin2.electives.proto.api.AdminService.SetStudentSelectionsRequest>()
            ?: return badRequest()

        try {
            @OptIn(CreatesTransaction::class)
            electiveSelectionService.forceSetAllStudentSelections(id, req.selectionsMap)

            ok()
        } catch (e: NotFoundException) {
            return when (e.entity) {
                NotFoundEntity.STUDENT -> badRequest("Student not found")
                NotFoundEntity.ELECTIVE -> badRequest("One or more electives not found")
                NotFoundEntity.SUBJECT -> badRequest("One or more subjects not found")

                else -> throw e
            }
        }
    }
}

class AdminElectivesController(
    private val electiveService: ElectiveService,
) {
    fun Application.register() {
        adminRoutes {
            context(electiveService) {
                get<Admin.Electives> { handleGetElectives() }

                get<Admin.Electives.Id> { params -> handleGetElective(params.id) }

                put<Admin.Electives.Id> { params -> handlePutElective(params.id) }

                delete<Admin.Electives.Id> { params -> handleDeleteElective(params.id) }

                patch<Admin.Electives.Id> { params -> handlePatchElective(params.id) }
            }
        }
    }

    private suspend fun RoutingContext.handlePutElective(id: Int) {
        val elective = call.parseOrNull<th.ac.bodin2.electives.proto.api.Elective>()
            ?: return badRequest()

        if (elective.id != id) return badRequest("ID in URL does not match body")

        @OptIn(CreatesTransaction::class)
        electiveService.create(
            id = elective.id,
            name = elective.name,
            team = if (elective.hasTeamId()) elective.teamId else null,
            startDate = if (elective.hasStartDate()) elective.startDate.inLocalDateTimeBySeconds else null,
            endDate = if (elective.hasEndDate()) elective.endDate.inLocalDateTimeBySeconds else null
        )

        ok()
    }

    private suspend fun RoutingContext.handleDeleteElective(id: Int) {
        try {
            @OptIn(CreatesTransaction::class)
            electiveService.delete(id)
            ok()
        } catch (_: NotFoundException) {
            badRequest("Elective not found")
        }
    }

    private suspend fun RoutingContext.handlePatchElective(id: Int) {
        val req = call.parseOrNull<th.ac.bodin2.electives.proto.api.AdminService.ElectivePatch>()
            ?: return badRequest()

        val update = ElectiveService.ElectiveUpdate(
            name = if (req.hasName()) req.name else null,
            team = if (req.hasTeamId()) req.teamId else null,
            startDate = if (req.hasStartDate()) req.startDate.inLocalDateTimeBySeconds else null,
            endDate = if (req.hasEndDate()) req.endDate.inLocalDateTimeBySeconds else null,
            setTeam = req.patchTeamId,
            setStartDate = req.patchStartDate,
            setEndDate = req.patchEndDate,
        )

        try {
            @OptIn(CreatesTransaction::class)
            electiveService.update(id, update)
            ok()
        } catch (e: NotFoundException) {
            return when (e.entity) {
                NotFoundEntity.ELECTIVE -> badRequest("Elective not found")
                NotFoundEntity.TEAM -> badRequest("Team not found")

                else -> throw e
            }
        }
    }
}

class AdminElectivesSubjectsController(private val electiveService: ElectiveService) {
    fun Application.register() {
        adminRoutes {
            context(electiveService) {
                get<Admin.Electives.Id.Subjects> { params -> handleGetElectiveSubjects(params.parent.id) }

                put<Admin.Electives.Id.Subjects> { params -> handlePutElectiveSubjects(params.parent.id) }
            }
        }
    }

    private suspend fun RoutingContext.handlePutElectiveSubjects(electiveId: Int) {
        val req = call.parseOrNull<th.ac.bodin2.electives.proto.api.AdminService.SetElectiveSubjectsRequest>()
            ?: return badRequest()

        try {
            @OptIn(CreatesTransaction::class)
            electiveService.setSubjects(electiveId, req.subjectIdsList)

            ok()
        } catch (e: NotFoundException) {
            return when (e.entity) {
                NotFoundEntity.ELECTIVE -> badRequest("Elective not found")
                NotFoundEntity.SUBJECT -> badRequest("One or more subjects not found")

                else -> throw e
            }
        }
    }
}

class AdminSubjectsController(private val subjectService: SubjectService) {
    fun Application.register() {
        adminRoutes {
            get<Admin.Subjects> { handleGetSubjects() }

            get<Admin.Subjects.Id> { params -> handleGetSubject(params.id) }

            put<Admin.Subjects.Id> { params -> handlePutSubject(params.id) }

            delete<Admin.Subjects.Id> { params -> handleDeleteSubject(params.id) }

            patch<Admin.Subjects.Id> { params -> handlePatchSubject(params.id) }
        }
    }

    private suspend fun RoutingContext.handleGetSubjects() {
        call.respond(listSubjectsResponse {
            transaction {
                subjects += subjectService.getAll().map { it.toProto(withDescription = false, withTeachers = true) }
            }
        })
    }

    private suspend fun RoutingContext.handleGetSubject(id: Int) {
        val response = transaction { subjectService.getById(id)?.toProto(withDescription = true, withTeachers = true) }
            ?: return notFound()

        call.respond(response)
    }

    private suspend fun RoutingContext.handlePutSubject(id: Int) {
        val subject = call.parseOrNull<th.ac.bodin2.electives.proto.api.Subject>()
            ?: return badRequest()

        if (subject.id != id) return badRequest("ID in URL does not match body")

        @OptIn(CreatesTransaction::class)
        subjectService.create(
            id = subject.id,
            name = subject.name,
            description = if (subject.hasDescription()) subject.description else null,
            code = subject.code,
            tag = subject.tag,
            location = subject.location,
            capacity = subject.capacity,
            team = if (subject.hasTeamId()) subject.teamId else null,
            teacherIds = subject.teachersList.map { it.id },
            thumbnailUrl = if (subject.hasThumbnailUrl()) subject.thumbnailUrl else null,
            imageUrl = if (subject.hasImageUrl()) subject.imageUrl else null,
        )

        ok()
    }

    private suspend fun RoutingContext.handleDeleteSubject(id: Int) {
        try {
            @OptIn(CreatesTransaction::class)
            subjectService.delete(id)
            ok()
        } catch (_: NotFoundException) {
            badRequest("Subject not found")
        }
    }

    private suspend fun RoutingContext.handlePatchSubject(id: Int) {
        val req = call.parseOrNull<th.ac.bodin2.electives.proto.api.AdminService.SubjectPatch>()
            ?: return badRequest()

        val update = SubjectService.SubjectUpdate(
            name = if (req.hasName()) req.name else null,
            description = if (req.hasDescription()) req.description else null,
            code = if (req.hasCode()) req.code else null,
            tag = if (req.hasTag()) req.tag else null,
            location = if (req.hasLocation()) req.location else null,
            capacity = if (req.hasCapacity()) req.capacity else null,
            team = if (req.hasTeamId()) req.teamId else null,
            teacherIds = req.teachersList.map { it.id },
            thumbnailUrl = if (req.hasThumbnailUrl()) req.thumbnailUrl else null,
            imageUrl = if (req.hasImageUrl()) req.imageUrl else null,
            patchTeachers = req.patchTeachers,
        )

        try {
            @OptIn(CreatesTransaction::class)
            subjectService.update(id, update)
            ok()
        } catch (e: NotFoundException) {
            return when (e.entity) {
                NotFoundEntity.SUBJECT -> badRequest("Subject not found")
                NotFoundEntity.TEACHER -> badRequest("One or more teachers not found")
                NotFoundEntity.TEAM -> badRequest("Team not found")

                else -> throw e
            }
        }
    }
}

class AdminTeamsController(private val teamService: TeamService) {
    fun Application.register() {
        adminRoutes {
            get<Admin.Teams> { handleGetTeams() }

            get<Admin.Teams.Id> { params -> handleGetTeam(params.id) }

            put<Admin.Teams.Id> { params -> handlePutTeam(params.id) }

            delete<Admin.Teams.Id> { params -> handleDeleteTeam(params.id) }

            patch<Admin.Teams.Id> { params -> handlePatchTeam(params.id) }
        }
    }

    private suspend fun RoutingContext.handleGetTeams() {
        call.respond(listTeamsResponse {
            transaction {
                teams += teamService.getAll().map { it.toProto() }
            }
        })
    }

    private suspend fun RoutingContext.handleGetTeam(id: Int) {
        val response = transaction { teamService.getById(id)?.toProto() }
            ?: return notFound()

        call.respond(response)
    }

    private suspend fun RoutingContext.handlePutTeam(id: Int) {
        val team = call.parseOrNull<th.ac.bodin2.electives.proto.api.Team>()
            ?: return badRequest()

        if (team.id != id) return badRequest("ID in URL does not match body")

        @OptIn(CreatesTransaction::class)
        teamService.create(team.id, team.name)

        ok()
    }

    private suspend fun RoutingContext.handleDeleteTeam(id: Int) {
        try {
            @OptIn(CreatesTransaction::class)
            teamService.delete(id)
            ok()
        } catch (_: NotFoundException) {
            badRequest("Team not found")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private suspend fun RoutingContext.handlePatchTeam(id: Int) {
        val req = call.parseOrNull<th.ac.bodin2.electives.proto.api.AdminService.TeamPatch>()
            ?: return badRequest()

        val update = TeamService.TeamUpdate(
            name = if (req.hasName()) req.name else null,
        )

        try {
            @OptIn(CreatesTransaction::class)
            teamService.update(id, update)
            ok()
        } catch (e: NotFoundException) {
            return when (e.entity) {
                NotFoundEntity.TEAM -> badRequest("Team not found")

                else -> throw e
            }
        }
    }
}

private val Long.inLocalDateTimeBySeconds: LocalDateTime
    get() = Instant.ofEpochSecond(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()

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

        // GET: User, DELETE, PATCH: UserPatch, PUT: AddUserRequest
        @Resource("{id}")
        class Id(val parent: Users, val id: Int) {
            // GET: UsersService.StudentSelections, PUT: SetStudentSelectionsRequest
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
        class Id(val parent: Subjects, val id: Int)
    }

    // GET: ListTeamsResponse
    @Resource("teams")
    class Teams(val parent: Admin) {
        // PUT: Team, GET: Team, DELETE, PATCH: TeamPatch
        @Resource("{id}")
        class Id(val parent: Teams, val id: Int)
    }
}

private fun Application.adminRoutes(block: Routing.() -> Unit) {
    routing {
        authenticate(ADMIN_AUTHENTICATION) {
            rateLimit(RATE_LIMIT_ADMIN) {
                block()
            }
        }
    }
}