package th.ac.bodin2.electives.api.routes

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.application
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.selectAll
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
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.api.utils.unauthorized
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.models.Students
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.db.uId
import th.ac.bodin2.electives.proto.api.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import th.ac.bodin2.electives.proto.api.ElectivesService as ProtoElectivesService

val adminController = controller {
    val usersService: UsersService by dependencies
    val electiveSelectionService: ElectiveSelectionService by dependencies
    val electiveService: ElectiveService by dependencies
    val subjectService: SubjectService by dependencies
    val teamService: TeamService by dependencies

    listOf(
        adminAuthController,
        AdminUsersController(usersService),
        AdminUsersSelectionsController(electiveSelectionService),
        AdminElectivesController(electiveService, teamService),
        AdminElectivesSubjectsController(electiveService),
        AdminSubjectsController(subjectService),
        AdminTeamsController(teamService),
    ).forEach { ctl -> ctl.apply { this@controller.register() } }

    routing {
        // Just preventing random crawlers from finding this route
        // and potentially abusing it
        authenticate(ADMIN_AUTHENTICATION, optional = true) {
            resource<Admin> {
                handle {
                    if (call.request.httpMethod == HttpMethod.Head && call.isAdmin()) {
                        return@handle ok()
                    }

                    call.response.status(HttpStatusCode.NotFound)
                }
            }
        }
    }
}

val adminAuthController = controller {
    val adminAuthService: AdminAuthService by dependencies

    routing {
        rateLimit(RATE_LIMIT_ADMIN_AUTH) {
            get<Admin.Challenge> {
                call.respond(
                    AdminService.ChallengeResponse(
                        challenge = adminAuthService.newChallenge(),
                    )
                )
            }

            post<Admin.Auth> {
                val req = call.parseOrNull<AuthService.AuthenticateRequest>() ?: return@post notFound()

                try {
                    when (val result = adminAuthService.createSession(
                        id = req.id.toUInt(),
                        signature = req.password,
                        aud = req.client_name,
                        ip = call.request.origin.remoteAddress,
                    )) {
                        is CreateSessionResult.Success -> {
                            call.respond(
                                AuthService.AuthenticateResponse(
                                    token = result.token,
                                )
                            )
                        }

                        is CreateSessionResult.NoChallenge,
                        is CreateSessionResult.IPNotAllowed,
                        is CreateSessionResult.UserNotAdmin -> notFound()

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

class AdminUsersController(
    private val usersService: UsersService,
) : Controller {
    override fun Application.register() {
        adminRoutes {
            get<Admin.Users.Students> { params ->
                call.respond(transaction {
                    val (students, count) =
                        @OptIn(Transactional::class)
                        usersService.getStudents(params.page, params.query.ifBlank { null })

                    AdminService.ListUsersResponse(
                        users = students.map { it.toProto() },
                        total = count.toInt(),
                    )
                })
            }

            get<Admin.Users.Teachers> { params ->
                call.respond(transaction {
                    val (teachers, count) =
                        @OptIn(Transactional::class)
                        usersService.getTeachers(params.page, params.query.ifBlank { null })

                    AdminService.ListUsersResponse(
                        users = teachers.map { it.toProto() },
                        total = count.toInt(),
                    )
                })
            }

            put<Admin.Users.Id> { params -> handlePutUser(params.id) }

            patch<Admin.Users.Id> { params -> handlePatchUser(params.id) }

            delete<Admin.Users.Id> { params -> handleDeleteUser(params.id) }

            post<Admin.Users.Bulk> { handleBulkAddUsers() }

            delete<Admin.Users.Bulk> { handleBulkDeleteUsers() }
        }
    }

    private suspend fun RoutingContext.handlePutUser(id: UInt) {
        val req = call.parseOrNull<AdminService.AddUserRequest>()
            ?: return badRequest()
        val user = req.user ?: return badRequest()
        if (user.uId != id) return badRequest("ID in URL does not match body")

        try {
            val protoOrNull = transaction {
                val created = when (user.type) {
                    UserType.STUDENT -> usersService.createStudent(
                        id = user.uId,
                        firstName = user.first_name,
                        middleName = user.middle_name,
                        lastName = user.last_name,
                        password = req.password,
                        avatarUrl = user.avatar_url,
                        teams = req.team_ids.map { it.toUInt() }.ifEmpty { null }
                    )

                    UserType.TEACHER -> usersService.createTeacher(
                        id = user.uId,
                        firstName = user.first_name,
                        middleName = user.middle_name,
                        lastName = user.last_name,
                        password = req.password,
                        avatarUrl = user.avatar_url,
                    )

                    else -> return@transaction null
                }

                when (created) {
                    is Student -> created.toProto()
                    is Teacher -> created.toProto()
                    else -> null
                }
            }

            protoOrNull ?: return badRequest("Unsupported user type")
            call.respond(protoOrNull)
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

    private suspend fun RoutingContext.handlePatchUser(id: UInt) {
        val req = call.parseOrNull<AdminService.UserPatch>()
            ?: return badRequest()

        try {
            val proto = transaction {
                val type = usersService.getUserType(id)

                val update = UsersService.UserUpdate(
                    firstName = req.first_name,
                    middleName = req.middle_name,
                    lastName = req.last_name,
                    avatarUrl = req.avatar_url,
                    setMiddleName = req.patch_middle_name,
                    setLastName = req.patch_last_name,
                    setAvatarUrl = req.patch_avatar_url,
                )

                @OptIn(Transactional::class)
                val proto = when (type) {
                    UserType.STUDENT -> usersService.updateStudent(
                        id,
                        UsersService.StudentUpdate(
                            update,
                            teams = if (req.patch_teams) req.teams.map { it.toUInt() } else null
                        )
                    ).toProto()

                    UserType.TEACHER -> usersService.updateTeacher(id, UsersService.TeacherUpdate(update)).toProto()

                    else -> throw IllegalStateException("Unreachable case: $type")
                }

                if (req.new_password != null) {
                    @OptIn(Transactional::class)
                    usersService.setPassword(id, req.new_password!!)
                }

                proto
            }

            call.respond(proto)
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

    private suspend fun RoutingContext.handleDeleteUser(id: UInt) {
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
        val user = this.user!!
        return UsersService.UserData(
            id = user.uId,
            firstName = user.first_name,
            middleName = user.middle_name,
            lastName = user.last_name,
            avatarUrl = user.avatar_url,
            password = password,
        )
    }

    private val supportedBulkAddTypes = setOf(UserType.STUDENT, UserType.TEACHER)

    // @TODO: Create user with one single method call: createUsers()
    private suspend fun RoutingContext.handleBulkAddUsers() {
        val req = call.parseOrNull<AdminService.BulkAddUsersRequest>()
            ?: return badRequest()

        val inserts = req.values.groupBy { it.user?.type }

        if (inserts.keys.minus(supportedBulkAddTypes).any { key ->
                (inserts[key]?.let { it.isNotEmpty() }) ?: false
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
                teams = it.team_ids.map { id -> id.toUInt() },
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
                AdminService.ListUsersResponse(
                    users = created,
                    total = created.size,
                )
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
            usersService.deleteUsers(req.user_ids.map { it.toUInt() })
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
            put<Admin.Users.Id.Selections> { params -> handlePutStudentSelections(params.parent.id) }
        }
    }

    private suspend fun RoutingContext.handlePutStudentSelections(id: UInt) {
        val req = call.parseOrNull<AdminService.SetStudentSelectionsRequest>()
            ?: return badRequest()

        try {
            @OptIn(Transactional::class)
            electiveSelectionService.forceSetAllStudentSelections(
                id,
                req.selections.mapKeys { it.key.toUInt() }.mapValues { it.value.toUInt() }
            )

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
    private val teamService: TeamService,
) : Controller {
    override fun Application.register() {
        adminRoutes {
            get<Admin.Electives.Progress> { params -> handleGetElectivesProgress(params.ids) }

            context(electiveService) {
                put<Admin.Electives.Id> { params -> handlePutElective(params.id) }

                delete<Admin.Electives.Id> { params -> handleDeleteElective(params.id) }

                patch<Admin.Electives.Id> { params -> handlePatchElective(params.id) }
            }
        }
    }

    private suspend fun RoutingContext.handlePutElective(id: UInt) {
        val elective = call.parseOrNull<Elective>()
            ?: return badRequest()

        if (elective.uId != id) return badRequest("ID in URL does not match body")

        try {
            @OptIn(Transactional::class)
            electiveService.create(
                id = elective.uId,
                name = elective.name,
                team = if (elective.team_id != null) elective.team_id!!.toUInt() else null,
                startDate = if (elective.start_date != null) elective.start_date!!.secondsToUTCDateTime else null,
                endDate = if (elective.end_date != null) elective.end_date!!.secondsToUTCDateTime else null
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

    private suspend fun RoutingContext.handleDeleteElective(id: UInt) {
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

    private suspend fun RoutingContext.handleGetElectivesProgress(idsParam: String) {
        val ids = idsParam.split(",").mapNotNull { it.trim().toUIntOrNull() }
        if (ids.isEmpty()) return badRequest()

        call.respond(
            AdminService.ListElectivesEnrolledCounts(
                counts = transaction {
                    val totalStudents by lazy { Students.selectAll().count().toInt() }

                    buildMap {
                        for (electiveId in ids) {
                            val elective = electiveService.getById(electiveId) ?: continue
                            val enrolledCount = electiveService.getEnrolledCount(electiveId)
                            val teamId = elective.teamId
                            val total = if (teamId != null) {
                                teamService.getMemberCount(teamId.value)
                            } else {
                                totalStudents
                            }

                            put(
                                electiveId.toInt(), AdminService.ListElectivesEnrolledCounts.Counts(
                                    selected = enrolledCount,
                                    total = total,
                                )
                            )
                        }
                    }
                }
            ))
    }

    private suspend fun RoutingContext.handlePatchElective(id: UInt) {
        val req = call.parseOrNull<AdminService.ElectivePatch>()
            ?: return badRequest()

        val update = ElectiveService.ElectiveUpdate(
            name = req.name,
            team = if (req.team_id != null) req.team_id!!.toUInt() else null,
            startDate = if (req.start_date != null) req.start_date!!.secondsToUTCDateTime else null,
            endDate = if (req.end_date != null) req.end_date!!.secondsToUTCDateTime else null,
            setTeam = req.patch_team_id,
            setStartDate = req.patch_start_date,
            setEndDate = req.patch_end_date,
        )

        try {
            val proto = transaction {
                @OptIn(Transactional::class)
                electiveService.update(id, update).toProto()
            }
            call.respond(proto)
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
                put<Admin.Electives.Id.Subjects> { params -> handlePutElectiveSubjects(params.parent.id) }
            }
        }
    }

    private suspend fun RoutingContext.handlePutElectiveSubjects(electiveId: UInt) {
        val req = call.parseOrNull<AdminService.SetElectiveSubjectsRequest>()
            ?: return badRequest()

        try {
            @OptIn(Transactional::class)
            electiveService.setSubjects(electiveId, req.subject_ids.map { it.toUInt() })

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

            get<Admin.Subjects.Id.ElectiveIds> { params -> handleGetSubjectElectiveIds(params.parent.id) }
        }
    }

    private suspend fun RoutingContext.handleGetSubjects() {
        call.respond(
            ProtoElectivesService.ListSubjectsResponse(
                subjects = transaction {
                    subjectService.getAll().map { it.toProto(withDescription = false, withTeachers = true) }
                }
            ))
    }

    private suspend fun RoutingContext.handleGetSubject(id: UInt) {
        val response = transaction { subjectService.getById(id)?.toProto(withDescription = true, withTeachers = true) }
            ?: return notFound()

        call.respond(response)
    }

    private suspend fun RoutingContext.handlePutSubject(id: UInt) {
        val subject = call.parseOrNull<Subject>()
            ?: return badRequest()

        if (subject.uId != id) return badRequest("ID in URL does not match body")
        if (subject.teachers.isNotEmpty()) return badRequest("Can't add teachers into a subject immediately")

        try {
            @OptIn(Transactional::class)
            subjectService.create(
                id = subject.uId,
                name = subject.name,
                description = subject.description,
                code = subject.code,
                tag = subject.tag,
                location = subject.location,
                capacity = subject.capacity,
                team = if (subject.team_id != null) subject.team_id!!.toUInt() else null,
                thumbnailUrl = subject.thumbnail_url,
                imageUrl = subject.image_url,
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

    private suspend fun RoutingContext.handleDeleteSubject(id: UInt) {
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

    private suspend fun RoutingContext.handlePatchSubject(id: UInt) {
        val req = call.parseOrNull<AdminService.SubjectPatch>()
            ?: return badRequest()

        val update = SubjectService.SubjectUpdate(
            name = req.name,
            tag = req.tag,
            capacity = req.capacity,
            teacherIds = if (req.patch_teachers) req.teachers.map { it.toUInt() } else null,
            electiveId = if (req.elective_id != null) req.elective_id!!.toUInt() else null,
            description = req.description,
            code = req.code,
            location = req.location,
            team = if (req.team_id != null) req.team_id!!.toUInt() else null,
            thumbnailUrl = req.thumbnail_url,
            imageUrl = req.image_url,
            setCode = req.patch_code,
            setTeam = req.patch_team_id,
            setLocation = req.patch_location,
            setImageUrl = req.patch_image_url,
            setDescription = req.patch_description,
            setThumbnailUrl = req.patch_thumbnail_url,
        )

        try {
            val proto = transaction {
                @OptIn(Transactional::class)
                subjectService.update(id, update).toProto(withDescription = true, withTeachers = true)
            }
            call.respond(proto)
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

    private suspend fun RoutingContext.handleGetSubjectElectiveIds(id: UInt) {
        val ids = transaction { subjectService.getElectiveIds(id) }
            ?: return notFound()

        call.respond(
            AdminService.SubjectElectiveIds(
                elective_ids = ids.map { it.toInt() },
            )
        )
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

            get<Admin.Teams.Id.Members> { params ->
                handleGetTeamMembers(
                    params.parent.id,
                    params.page,
                    params.query.ifBlank { null })
            }
        }
    }

    private suspend fun RoutingContext.handleGetTeamMembers(teamId: UInt, page: Int, query: String?) {
        try {
            call.respond(transaction {
                val (members, count) = @OptIn(Transactional::class) teamService.getMembers(teamId, page, query)

                AdminService.ListUsersResponse(
                    users = members.map { it.toProto() },
                    total = count.toInt(),
                )
            })
        } catch (_: EntityNotFoundException) {
            notFound("Team not found")
        }
    }

    private suspend fun RoutingContext.handleGetTeams() {
        call.respond(
            AdminService.ListTeamsResponse(
                teams = transaction {
                    teamService.getAll().map { it.toProto() }
                }
            ))
    }

    private suspend fun RoutingContext.handleGetTeam(id: UInt) {
        val response = transaction { teamService.getById(id)?.toProto() }
            ?: return notFound()

        call.respond(response)
    }

    private suspend fun RoutingContext.handlePutTeam(id: UInt) {
        val team = call.parseOrNull<Team>()
            ?: return badRequest()

        if (team.uId != id) return badRequest("ID in URL does not match body")

        try {
            @OptIn(Transactional::class)
            teamService.create(team.uId, team.name)
            ok()
        } catch (_: ConflictException) {
            conflict("Team with the same ID already exists")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private suspend fun RoutingContext.handleDeleteTeam(id: UInt) {
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

    private suspend fun RoutingContext.handlePatchTeam(id: UInt) {
        val req = call.parseOrNull<AdminService.TeamPatch>()
            ?: return badRequest()

        val update = TeamService.TeamUpdate(
            name = req.name,
        )

        try {
            val proto = transaction {
                @OptIn(Transactional::class)
                teamService.update(id, update).toProto()
            }
            call.respond(proto)
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
        call.respond(
            AdminService.TeamMemberCounts(
                member_counts = transaction { teamService.getMemberCounts() }.mapKeys { it.key.toInt() },
            )
        )
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

    @Resource("users")
    class Users(val parent: Admin) {
        // @TODO: Add route tests
        // POST: BulkAddUsersRequest, DELETE: BulkDeleteUsersRequest
        @Resource("bulk")
        class Bulk(val parent: Users)

        // GET: ListUsersResponse
        @Resource("students")
        class Students(val parent: Users, val page: Int = 1, val query: String = "")

        // GET: ListUsersResponse
        @Resource("teachers")
        class Teachers(val parent: Users, val page: Int = 1, val query: String = "")

        // DELETE, PATCH: UserPatch, PUT: AddUserRequest
        @Resource("{id}")
        class Id(
            val parent: Users,
            @Serializable(with = UIntStringSerializer::class)
            val id: UInt
        ) {
            // PUT: SetStudentSelectionsRequest
            @Resource("selections")
            class Selections(val parent: Id)
        }
    }

    @Resource("electives")
    class Electives(val parent: Admin) {
        // GET: ListElectivesEnrolledCounts
        @Resource("progress")
        class Progress(val parent: Electives, val ids: String)

        // PUT: Elective, DELETE, PATCH: ElectivePatch
        @Resource("{id}")
        class Id(
            val parent: Electives,
            @Serializable(with = UIntStringSerializer::class)
            val id: UInt
        ) {
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
        class Id(
            val parent: Subjects,
            @Serializable(with = UIntStringSerializer::class)
            val id: UInt
        ) {
            @Resource("elective-ids")
            class ElectiveIds(val parent: Id)
        }
    }

    // GET: ListTeamsResponse
    @Resource("teams")
    class Teams(val parent: Admin) {
        // PUT: Team, GET: Team, DELETE, PATCH: TeamPatch
        @Resource("{id}")
        class Id(
            val parent: Teams,
            @Serializable(with = UIntStringSerializer::class)
            val id: UInt
        ) {
            // GET: ListUsersResponse
            @Resource("members")
            class Members(val parent: Id, val page: Int = 1, val query: String = "")
        }

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
