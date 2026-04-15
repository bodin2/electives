package th.ac.bodin2.electives.api.routes

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.application
import io.ktor.server.routing.routing
import io.ktor.server.websocket.*
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.ADMIN_AUTHENTICATION
import th.ac.bodin2.electives.api.RATE_LIMIT_ADMIN
import th.ac.bodin2.electives.api.RATE_LIMIT_ADMIN_AUTH
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.*
import th.ac.bodin2.electives.api.services.AdminAuthService.CreateSessionResult
import th.ac.bodin2.electives.api.services.NotificationsService
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.api.utils.unauthorized
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.proto.api.*
import th.ac.bodin2.electives.proto.api.AdminServiceKt.challengeResponse
import th.ac.bodin2.electives.proto.api.AdminServiceKt.listTeamsResponse
import th.ac.bodin2.electives.proto.api.AdminServiceKt.listUsersResponse
import th.ac.bodin2.electives.proto.api.AdminServiceKt.teamMemberCounts
import th.ac.bodin2.electives.proto.api.AuthServiceKt.authenticateResponse
import th.ac.bodin2.electives.proto.api.ElectivesServiceKt.listSubjectsResponse
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

val adminController = controller {
    val notificationsService: NotificationsService by dependencies
    val usersService: UsersService by dependencies
    val electiveSelectionService: ElectiveSelectionService by dependencies
    val electiveService: ElectiveService by dependencies
    val subjectService: SubjectService by dependencies
    val teamService: TeamService by dependencies

    listOf(
        adminAuthController,
        AdminUsersController(usersService),
        AdminUsersSelectionsController(electiveSelectionService),
        AdminElectivesController(electiveService),
        AdminElectivesSubjectsController(electiveService),
        AdminSubjectsController(subjectService),
        AdminTeamsController(teamService),
    ).forEach { ctl -> ctl.apply { this@controller.register() } }

    routing {
        authenticate(ADMIN_AUTHENTICATION, optional = true) {
            head<Admin> {
                if (call.isAdmin()) {
                    return@head ok()
                }

                call.response.status(HttpStatusCode.NotFound)
            }
        }

        resource<Admin.Notifications> {
            webSocket {
                notificationsService.apply { handleAdminConnection() }
            }
        }
    }
}

val adminAuthController = controller {
    val adminAuthService: AdminAuthService by dependencies

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

        adminRoutes {
            post<Admin.LogOut> {
                adminAuthService.clearSession()
                ok()
            }
        }
    }
}

class AdminUsersController(
    private val usersService: UsersService,
) : Controller {
    override fun Application.register() {
        adminRoutes {
            get<Admin.Users.Students> { params ->
                call.respond(listUsersResponse {
                    val (students, count) =
                        @OptIn(Transactional::class)
                        usersService.getStudents(params.page)

                    users += students.map { it.toProto() }
                    total = count.toInt()
                })
            }

            get<Admin.Users.Teachers> { params ->
                call.respond(listUsersResponse {
                    val (teachers, count) =
                        @OptIn(Transactional::class)
                        usersService.getTeachers(params.page)

                    users += teachers.map { it.toProto() }
                    total = count.toInt()
                })
            }

            get<Admin.Users.Id> { params -> context(usersService) { handleGetUser(params.id) } }

            put<Admin.Users.Id> { params -> handlePutUser(params.id) }

            patch<Admin.Users.Id> { params -> handlePatchUser(params.id) }

            delete<Admin.Users.Id> { params -> handleDeleteUser(params.id) }

            post<Admin.Users.Bulk> { handleBulkAddUsers() }

            delete<Admin.Users.Bulk> { handleBulkDeleteUsers() }
        }
    }

    private suspend fun RoutingContext.handlePutUser(id: Int) {
        val req = call.parseOrNull<AdminService.AddUserRequest>()
            ?: return badRequest()
        val user = req.user
        if (user.id != id) return badRequest("ID in URL does not match body")

        try {
            val userOrNull = transaction {
                when (user.type) {
                    UserType.STUDENT -> usersService.createStudent(
                        id = user.id,
                        firstName = user.firstName,
                        middleName = if (user.hasMiddleName()) user.middleName else null,
                        lastName = user.lastName,
                        password = req.password,
                        avatarUrl = if (user.hasAvatarUrl()) user.avatarUrl else null,
                        teams = req.teamIdsList.ifEmpty { null }
                    )

                    UserType.TEACHER -> usersService.createTeacher(
                        id = user.id,
                        firstName = user.firstName,
                        middleName = if (user.hasMiddleName()) user.middleName else null,
                        lastName = user.lastName,
                        password = req.password,
                        avatarUrl = if (user.hasAvatarUrl()) user.avatarUrl else null
                    )

                    else -> null
                }
            }

            when (userOrNull) {
                is Student -> call.respond(userOrNull.toProto())
                is Teacher -> call.respond(userOrNull.toProto())

                else -> badRequest("Unsupported user type")
            }
        } catch (_: IllegalArgumentException) {
            badRequest("Password does not meet the requirements")
        } catch (_: EntityNotFoundException) {
            badRequest("One or more specified teams not found")
        } catch (_: ConflictException) {
            conflict("User with the same ID already exists")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private suspend fun RoutingContext.handlePatchUser(id: Int) {
        val req = call.parseOrNull<AdminService.UserPatch>()
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

                @OptIn(Transactional::class)
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
                    @OptIn(Transactional::class)
                    usersService.setPassword(id, req.newPassword)
                }
            }

            ok()
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.USER,
                ExceptionEntity.TEACHER,
                ExceptionEntity.STUDENT -> notFound("User not found")

                ExceptionEntity.TEAM -> badRequest("One or more teams not found")

                else -> throw e
            }
        } catch (_: NothingToUpdateException) {
            badRequest("Nothing to update")
        } catch (_: IllegalArgumentException) {
            badRequest("New password does not meet the requirements")
        }
    }

    private suspend fun RoutingContext.handleDeleteUser(id: Int) {
        try {
            @OptIn(Transactional::class)
            usersService.deleteUser(id)
            ok()
        } catch (_: EntityNotFoundException) {
            return notFound("User not found")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private fun AdminService.AddUserRequest.toUserInsert(): UsersService.UserData {
        return UsersService.UserData(
            id = user.id,
            firstName = user.firstName,
            middleName = if (user.hasMiddleName()) user.middleName else null,
            lastName = user.lastName,
            avatarUrl = if (user.hasAvatarUrl()) user.avatarUrl else null,
            password = password,
        )
    }

    private val supportedBulkAddTypes = setOf(UserType.STUDENT, UserType.TEACHER)

    // @TODO: Create user with one single method call: createUsers()
    private suspend fun RoutingContext.handleBulkAddUsers() {
        val req = call.parseOrNull<AdminService.BulkAddUsersRequest>()
            ?: return badRequest()

        val inserts = req.valuesList.groupBy { it.user.type }

        if (!inserts.keys.minus(supportedBulkAddTypes).any { key ->
                (inserts[key]?.let { !it.isEmpty() }) ?: false
            }) {
            return badRequest("Unsupported user types")
        }

        val teacherInserts = inserts[UserType.TEACHER]?.map {
            UsersService.TeacherInsert(
                user = it.toUserInsert(),
            )
        }

        val studentInserts = inserts[UserType.STUDENT]?.map {
            UsersService.StudentInsert(
                user = it.toUserInsert(),
                teams = it.teamIdsList,
            )
        }

        try {
            // Dedupe transactions
            @OptIn(Transactional::class)
            val created = transaction {
                buildList {
                    if (!teacherInserts.isNullOrEmpty()) {
                        usersService.createTeachers(teacherInserts).forEach { add(it.toProto()) }
                    }

                    if (!studentInserts.isNullOrEmpty()) {
                        usersService.createStudents(studentInserts).forEach { add(it.toProto()) }
                    }
                }
            }

            call.respond(
                listUsersResponse {
                    users += created
                    total = created.size
                }
            )
        } catch (e: UsersService.BatchOperationException) {
            when (e) {
                is UsersService.BatchOperationException.InvalidUserData -> when (e.cause) {
                    is IllegalArgumentException -> badRequest("Password for user ${e.id} does not meet requirements")
                }

                is UsersService.BatchOperationException.MissingTeams -> badRequest("One or more specified teams not found")

                is UsersService.BatchOperationException.ConflictingEntities -> conflict("One or more users with the same ID already exists")

                else -> throw e
            }
        }
    }

    private suspend fun RoutingContext.handleBulkDeleteUsers() {
        val req = call.parseOrNull<AdminService.BulkDeleteUsersRequest>()
            ?: return badRequest()

        try {
            @OptIn(Transactional::class)
            usersService.deleteUsers(req.userIdsList)
            ok()
        } catch (e: UsersService.BatchOperationException.NotFoundEntities) {
            return badRequest("Users not found: ${e.ids.joinToString(", ")}")
        }
    }
}

class AdminUsersSelectionsController(
    private val electiveSelectionService: ElectiveSelectionService,
) : Controller {
    override fun Application.register() {
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
        val req = call.parseOrNull<AdminService.SetStudentSelectionsRequest>()
            ?: return badRequest()

        try {
            @OptIn(Transactional::class)
            electiveSelectionService.forceSetAllStudentSelections(id, req.selectionsMap)

            ok()
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.STUDENT -> notFound("Student not found")
                ExceptionEntity.ELECTIVE -> badRequest("One or more electives not found")
                ExceptionEntity.SUBJECT -> badRequest("One or more subjects not found")

                else -> throw e
            }
        } catch (_: IllegalArgumentException) {
            badRequest("One or more subjects are not part of their respective electives")
        }
    }
}

class AdminElectivesController(
    private val electiveService: ElectiveService,
) : Controller {
    override fun Application.register() {
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
        val elective = call.parseOrNull<Elective>()
            ?: return badRequest()

        if (elective.id != id) return badRequest("ID in URL does not match body")

        try {
            @OptIn(Transactional::class)
            electiveService.create(
                id = elective.id,
                name = elective.name,
                team = if (elective.hasTeamId()) elective.teamId else null,
                startDate = if (elective.hasStartDate()) elective.startDate.secondsToUTCDateTime else null,
                endDate = if (elective.hasEndDate()) elective.endDate.secondsToUTCDateTime else null
            )

            ok()
        } catch (_: EntityNotFoundException) {
            badRequest("Team not found")
        } catch (_: ConflictException) {
            conflict("Elective with the same ID already exists")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private suspend fun RoutingContext.handleDeleteElective(id: Int) {
        try {
            @OptIn(Transactional::class)
            electiveService.delete(id)
            ok()
        } catch (_: EntityNotFoundException) {
            notFound("Elective not found")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private suspend fun RoutingContext.handlePatchElective(id: Int) {
        val req = call.parseOrNull<AdminService.ElectivePatch>()
            ?: return badRequest()

        val update = ElectiveService.ElectiveUpdate(
            name = if (req.hasName()) req.name else null,
            team = if (req.hasTeamId()) req.teamId else null,
            startDate = if (req.hasStartDate()) req.startDate.secondsToUTCDateTime else null,
            endDate = if (req.hasEndDate()) req.endDate.secondsToUTCDateTime else null,
            setTeam = req.patchTeamId,
            setStartDate = req.patchStartDate,
            setEndDate = req.patchEndDate,
        )

        try {
            @OptIn(Transactional::class)
            electiveService.update(id, update)
            ok()
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.ELECTIVE -> notFound("Elective not found")
                ExceptionEntity.TEAM -> badRequest("Team not found")

                else -> throw e
            }
        } catch (_: NothingToUpdateException) {
            badRequest("Nothing to update")
        }
    }
}

class AdminElectivesSubjectsController(private val electiveService: ElectiveService) : Controller {
    override fun Application.register() {
        adminRoutes {
            context(electiveService) {
                get<Admin.Electives.Id.Subjects> { params -> handleGetElectiveSubjects(params.parent.id) }

                put<Admin.Electives.Id.Subjects> { params -> handlePutElectiveSubjects(params.parent.id) }
            }
        }
    }

    private suspend fun RoutingContext.handlePutElectiveSubjects(electiveId: Int) {
        val req = call.parseOrNull<AdminService.SetElectiveSubjectsRequest>()
            ?: return badRequest()

        try {
            @OptIn(Transactional::class)
            electiveService.setSubjects(electiveId, req.subjectIdsList)

            ok()
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.ELECTIVE -> notFound("Elective not found")
                ExceptionEntity.SUBJECT -> badRequest("One or more subjects not found")

                else -> throw e
            }
        }
    }
}

class AdminSubjectsController(private val subjectService: SubjectService) : Controller {
    override fun Application.register() {
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
        val subject = call.parseOrNull<Subject>()
            ?: return badRequest()

        if (subject.id != id) return badRequest("ID in URL does not match body")

        try {
            @OptIn(Transactional::class)
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
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.TEAM -> badRequest("Team not found")
                ExceptionEntity.TEACHER -> badRequest("One or more teachers not found")

                else -> throw e
            }
        } catch (_: ConflictException) {
            conflict("Subject with the same ID already exists")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private suspend fun RoutingContext.handleDeleteSubject(id: Int) {
        try {
            @OptIn(Transactional::class)
            subjectService.delete(id)
            ok()
        } catch (_: EntityNotFoundException) {
            notFound("Subject not found")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private suspend fun RoutingContext.handlePatchSubject(id: Int) {
        val req = call.parseOrNull<AdminService.SubjectPatch>()
            ?: return badRequest()

        val update = SubjectService.SubjectUpdate(
            name = if (req.hasName()) req.name else null,
            tag = if (req.hasTag()) req.tag else null,
            capacity = if (req.hasCapacity()) req.capacity else null,
            teacherIds = if (req.patchTeachers) req.teachersList else null,
            description = req.description,
            code = req.code,
            location = req.location,
            team = req.teamId,
            thumbnailUrl = req.thumbnailUrl,
            imageUrl = req.imageUrl,
            setCode = req.patchCode,
            setTeam = req.patchTeamId,
            setLocation = req.patchLocation,
            setImageUrl = req.patchImageUrl,
            setDescription = req.patchDescription,
            setThumbnailUrl = req.patchThumbnailUrl,
        )

        try {
            @OptIn(Transactional::class)
            subjectService.update(id, update)
            ok()
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.SUBJECT -> notFound("Subject not found")
                ExceptionEntity.TEACHER -> badRequest("One or more teachers not found")
                ExceptionEntity.TEAM -> badRequest("Team not found")

                else -> throw e
            }
        } catch (_: NothingToUpdateException) {
            badRequest("Nothing to update")
        }
    }
}

class AdminTeamsController(private val teamService: TeamService) : Controller {
    override fun Application.register() {
        adminRoutes {
            get<Admin.Teams> { handleGetTeams() }

            get<Admin.Teams.Id> { params -> handleGetTeam(params.id) }

            put<Admin.Teams.Id> { params -> handlePutTeam(params.id) }

            delete<Admin.Teams.Id> { params -> handleDeleteTeam(params.id) }

            patch<Admin.Teams.Id> { params -> handlePatchTeam(params.id) }

            get<Admin.Teams.MemberCounts> { handleGetTeamMemberCounts() }
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
        val team = call.parseOrNull<Team>()
            ?: return badRequest()

        if (team.id != id) return badRequest("ID in URL does not match body")

        try {
            @OptIn(Transactional::class)
            teamService.create(team.id, team.name)
            ok()
        } catch (_: ConflictException) {
            conflict("Team with the same ID already exists")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private suspend fun RoutingContext.handleDeleteTeam(id: Int) {
        try {
            @OptIn(Transactional::class)
            teamService.delete(id)
            ok()
        } catch (_: EntityNotFoundException) {
            notFound("Team not found")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private suspend fun RoutingContext.handlePatchTeam(id: Int) {
        val req = call.parseOrNull<AdminService.TeamPatch>()
            ?: return badRequest()

        val update = TeamService.TeamUpdate(
            name = if (req.hasName()) req.name else null,
        )

        try {
            @OptIn(Transactional::class)
            teamService.update(id, update)
            ok()
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.TEAM -> notFound("Team not found")

                else -> throw e
            }
        } catch (_: NothingToUpdateException) {
            badRequest("Nothing to update")
        }
    }

    private suspend fun RoutingContext.handleGetTeamMemberCounts() {
        call.respond(teamMemberCounts {
            val counts = transaction { teamService.getMemberCounts() }
            memberCounts.putAll(counts)
        })
    }
}

private val Long.secondsToUTCDateTime: LocalDateTime
    get() = Instant.ofEpochSecond(this)
        .atZone(ZoneId.of("UTC"))
        .toLocalDateTime()

@Suppress("UNUSED")
@Resource("/admin")
private class Admin {
    @Resource("challenge")
    class Challenge(val parent: Admin)

    @Resource("auth")
    class Auth(val parent: Admin)

    @Resource("logout")
    class LogOut(val parent: Admin)

    @Resource("notifications")
    class Notifications(val parent: Admin)

    @Resource("users")
    class Users(val parent: Admin) {
        // @TODO: Add route tests
        // POST: BulkAddUsersRequest, DELETE: BulkDeleteUsersRequest
        @Resource("bulk")
        class Bulk(val parent: Users)

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

        // GET: TeamMemberCounts
        @Resource("member-counts")
        class MemberCounts(val parent: Teams)
    }
}

private fun Application.adminRoutes(block: Route.() -> Unit) {
    routing {
        authenticate(ADMIN_AUTHENTICATION) {
            rateLimit(RATE_LIMIT_ADMIN) {
                block()
            }
        }
    }
}