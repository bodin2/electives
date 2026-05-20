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
import kotlinx.serialization.SerialName
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
import th.ac.bodin2.electives.proto.api.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

val adminController = controller {
    val usersService: UsersService by dependencies
    val enrollmentSelectionService: EnrollmentSelectionService by dependencies
    val enrollmentService: EnrollmentService by dependencies
    val subjectService: SubjectService by dependencies
    val groupService: GroupService by dependencies

    listOf(
        adminAuthController,
        AdminUsersController(usersService),
        AdminUsersSelectionsController(enrollmentSelectionService),
        AdminEnrollmentsController(enrollmentService, groupService),
        AdminEnrollmentsSubjectsController(enrollmentService),
        AdminSubjectsController(subjectService),
        AdminGroupsController(groupService),
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
                call.respond(AdminService.ChallengeResponse(challenge = adminAuthService.newChallenge()))
            }

            post<Admin.Auth> {
                val req = call.parseOrNull<AuthService.AuthenticateRequest>() ?: return@post notFound()

                try {
                    when (val result = adminAuthService.createSession(
                        id = req.id,
                        signature = req.password,
                        aud = req.client_name,
                        ip = call.request.origin.remoteAddress,
                    )) {
                        is CreateSessionResult.Success -> call.respond(AuthService.AuthenticateResponse(token = result.token))

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
                val (users, total) = transaction {
                    val (students, count) =
                        @OptIn(Transactional::class)
                        usersService.getStudents(params.page, params.query.ifBlank { null })

                    students.map { it.toProto() } to count.toInt()
                }

                call.respond(AdminService.ListUsersResponse(users = users, total = total))
            }

            get<Admin.Users.Teachers> { params ->
                val (users, total) = transaction {
                    val (teachers, count) =
                        @OptIn(Transactional::class)
                        usersService.getTeachers(params.page, params.query.ifBlank { null })

                    teachers.map { it.toProto() } to count.toInt()
                }

                call.respond(AdminService.ListUsersResponse(users = users, total = total))
            }

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
        val user = req.user ?: return badRequest("Missing user")
        if (user.id != id) return badRequest("ID in URL does not match body")

        try {
            val protoOrNull = transaction {
                val created = when (user.type) {
                    UserType.STUDENT -> {
                        val gradeId = req.grade_id
                        val roomId = req.room_id

                        // Students require fixed GRADE and ROOM group IDs to be set. PROGRAM is optional
                        if (gradeId == null || roomId == null) {
                            return@transaction null
                        }

                        usersService.createStudent(
                            id = user.id,
                            firstName = user.first_name,
                            prefix = user.prefix,
                            middleName = user.middle_name,
                            lastName = user.last_name,
                            password = req.password,
                            avatarUrl = user.avatar_url,
                            gradeId = gradeId,
                            roomId = roomId,
                            programId = req.program_id,
                            groupIds = req.group_ids.ifEmpty { null }
                        )
                    }

                    UserType.TEACHER -> usersService.createTeacher(
                        id = user.id,
                        firstName = user.first_name,
                        prefix = user.prefix,
                        middleName = user.middle_name,
                        lastName = user.last_name,
                        password = req.password,
                        avatarUrl = user.avatar_url,
                        groupIds = req.group_ids.ifEmpty { null }
                    )

                    else -> return@transaction null
                }

                when (created) {
                    is Student -> created.toProto()
                    is Teacher -> created.toProto()
                    else -> null
                }
            }

            protoOrNull
                ?: return badRequest("Unsupported user type or missing required group IDs (grade_id, room_id)")

            created(protoOrNull)
        } catch (e: IllegalArgumentException) {
            badRequest(e.message ?: "Invalid request")
        } catch (_: EntityNotFoundException) {
            badRequest("One or more specified groups not found")
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
            val proto = transaction {
                val type = usersService.getUserType(id)

                val update = UsersService.UserUpdate(
                    firstName = req.first_name,
                    prefix = req.prefix,
                    middleName = req.middle_name,
                    lastName = req.last_name,
                    avatarUrl = req.avatar_url,
                    setPrefix = req.patch_prefix,
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
                            groups = if (req.patch_groups) req.groups else null,
                            gradeId = req.grade_id,
                            roomId = req.room_id,
                            programId = req.program_id,
                            setProgramId = req.patch_program_id,
                        )
                    ).toProto()

                    UserType.TEACHER -> usersService.updateTeacher(
                        id,
                        UsersService.TeacherUpdate(
                            update,
                            groups = if (req.patch_groups) req.groups else null,
                        )
                    ).toProto()

                    else -> throw IllegalStateException("Unreachable case: $type")
                }


                req.new_password?.let {
                    @OptIn(Transactional::class)
                    usersService.setPassword(id, it)
                }

                proto
            }

            call.respond(proto)
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.USER,
                ExceptionEntity.TEACHER,
                ExceptionEntity.STUDENT -> notFound("User not found")

                ExceptionEntity.GROUP -> badRequest("One or more groups not found")

                else -> throw e
            }
        } catch (_: NothingToUpdateException) {
            badRequest("Nothing to update")
        } catch (e: IllegalArgumentException) {
            badRequest(e.message ?: "Invalid request")
        }
    }

    private suspend fun RoutingContext.handleDeleteUser(id: Int) {
        try {
            @OptIn(Transactional::class)
            usersService.deleteUser(id)
            noContent()
        } catch (_: EntityNotFoundException) {
            return notFound("User not found")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private fun AdminService.AddUserRequest.toUserInsert(): UsersService.UserData {
        val u = user!!
        return UsersService.UserData(
            id = u.id,
            firstName = u.first_name,
            prefix = u.prefix,
            middleName = u.middle_name,
            lastName = u.last_name,
            avatarUrl = u.avatar_url,
            password = password,
        )
    }

    private val supportedBulkAddTypes = setOf(UserType.STUDENT, UserType.TEACHER)

    // @TODO: Create user with one single method call: createUsers()
    private suspend fun RoutingContext.handleBulkAddUsers() {
        val req = call.parseOrNull<AdminService.BulkAddUsersRequest>()
            ?: return badRequest()

        if (req.values.any { it.user == null }) return badRequest("Missing user in one or more entries")

        val inserts = req.values.groupBy { it.user!!.type }

        if (inserts.keys.minus(supportedBulkAddTypes).any { key ->
                (inserts[key]?.isNotEmpty()) ?: false
            }) {
            return badRequest("Unsupported user types")
        }

        val teacherInserts = inserts[UserType.TEACHER]?.map {
            UsersService.TeacherInsert(
                user = it.toUserInsert(),
                groups = it.group_ids,
            )
        }

        val studentInserts = inserts[UserType.STUDENT]?.map {
            val gradeId = it.grade_id
            val roomId = it.room_id

            if (gradeId == null || roomId == null) {
                return badRequest("Student ${it.user!!.id} is missing one of grade_id, room_id")
            }

            UsersService.StudentInsert(
                user = it.toUserInsert(),
                gradeId,
                roomId,
                programId = it.program_id,
                groups = it.group_ids,
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

            created(AdminService.ListUsersResponse(users = created, total = created.size))
        } catch (e: UsersService.BatchOperationException) {
            when (e) {
                is UsersService.BatchOperationException.InvalidUserData -> when (e.cause) {
                    is IllegalArgumentException -> badRequest(
                        "User ${e.id} has invalid data: ${(e.cause as IllegalArgumentException).message ?: "unknown"}"
                    )
                }

                is UsersService.BatchOperationException.MissingGroups -> badRequest("One or more specified groups not found")

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
            usersService.deleteUsers(req.user_ids)
            noContent()
        } catch (e: UsersService.BatchOperationException.NotFoundEntities) {
            return badRequest("Users not found: ${e.ids.joinToString(", ")}")
        }
    }
}

class AdminUsersSelectionsController(
    private val enrollmentSelectionService: EnrollmentSelectionService,
) : Controller {
    override fun Application.register() {
        adminRoutes {
            put<Admin.Users.Id.Selections> { params -> handlePutStudentSelections(params.parent.id) }
        }
    }

    private suspend fun RoutingContext.handlePutStudentSelections(id: Int) {
        val req = call.parseOrNull<AdminService.SetStudentSelectionsRequest>()
            ?: return badRequest()

        try {
            @OptIn(Transactional::class)
            enrollmentSelectionService.forceSetAllStudentSelections(id, req.selections)

            noContent()
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.STUDENT -> notFound("Student not found")
                ExceptionEntity.ENROLLMENT -> badRequest("One or more enrollments not found")
                ExceptionEntity.SUBJECT -> badRequest("One or more subjects not found")

                else -> throw e
            }
        } catch (_: IllegalArgumentException) {
            badRequest("One or more subjects are not part of their respective enrollments")
        }
    }
}

class AdminEnrollmentsController(
    private val enrollmentService: EnrollmentService,
    private val groupService: GroupService,
) : Controller {
    override fun Application.register() {
        adminRoutes {
            get<Admin.Enrollments.Progress> { params -> handleGetEnrollmentsProgress(params.ids) }

            context(enrollmentService) {
                put<Admin.Enrollments.Id> { params -> handlePutEnrollment(params.id) }

                delete<Admin.Enrollments.Id> { params -> handleDeleteEnrollment(params.id) }

                patch<Admin.Enrollments.Id> { params -> handlePatchEnrollment(params.id) }
            }
        }
    }

    private suspend fun RoutingContext.handlePutEnrollment(id: Int) {
        val enrollment = call.parseOrNull<Enrollment>()
            ?: return badRequest()

        if (enrollment.id != id) return badRequest("ID in URL does not match body")

        try {
            @OptIn(Transactional::class)
            enrollmentService.create(
                id = enrollment.id,
                name = enrollment.name,
                group = enrollment.group_id,
                startDate = enrollment.start_date?.secondsToUTCDateTime,
                endDate = enrollment.end_date?.secondsToUTCDateTime
            )

            noContent()
        } catch (_: EntityNotFoundException) {
            badRequest("Group not found")
        } catch (_: ConflictException) {
            conflict("Enrollment with the same ID already exists")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private suspend fun RoutingContext.handleDeleteEnrollment(id: Int) {
        try {
            @OptIn(Transactional::class)
            enrollmentService.delete(id)
            noContent()
        } catch (_: EntityNotFoundException) {
            notFound("Enrollment not found")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private suspend fun RoutingContext.handleGetEnrollmentsProgress(idsParam: String) {
        val ids = idsParam.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (ids.isEmpty()) return badRequest()

        val counts = transaction {
            val totalStudents by lazy { Students.selectAll().count().toInt() }
            buildMap {
                for (enrollmentId in ids) {
                    val enrollment = enrollmentService.getById(enrollmentId) ?: continue
                    val enrolledCount = enrollmentService.getEnrolledCount(enrollmentId)
                    val groupId = enrollment.groupId
                    val total = if (groupId != null) {
                        groupService.getMemberCount(groupId.value)
                    } else {
                        totalStudents
                    }

                    put(
                        enrollmentId,
                        AdminService.ListEnrollmentsEnrolledCounts.Counts(
                            selected = enrolledCount,
                            total = total,
                        )
                    )
                }
            }
        }

        call.respond(AdminService.ListEnrollmentsEnrolledCounts(counts = counts))
    }

    private suspend fun RoutingContext.handlePatchEnrollment(id: Int) {
        val req = call.parseOrNull<AdminService.EnrollmentPatch>()
            ?: return badRequest()

        val update = EnrollmentService.EnrollmentUpdate(
            name = req.name,
            group = req.group_id,
            startDate = req.start_date?.secondsToUTCDateTime,
            endDate = req.end_date?.secondsToUTCDateTime,
            setGroup = req.patch_group_id,
            setStartDate = req.patch_start_date,
            setEndDate = req.patch_end_date,
        )

        try {
            val proto = transaction {
                @OptIn(Transactional::class)
                enrollmentService.update(id, update).toProto()
            }
            call.respond(proto)
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.ENROLLMENT -> notFound("Enrollment not found")
                ExceptionEntity.GROUP -> badRequest("Group not found")

                else -> throw e
            }
        } catch (_: NothingToUpdateException) {
            badRequest("Nothing to update")
        }
    }
}

class AdminEnrollmentsSubjectsController(private val enrollmentService: EnrollmentService) : Controller {
    override fun Application.register() {
        adminRoutes {
            context(enrollmentService) {
                put<Admin.Enrollments.Id.Subjects> { params -> handlePutEnrollmentSubjects(params.parent.id) }
            }
        }
    }

    private suspend fun RoutingContext.handlePutEnrollmentSubjects(enrollmentId: Int) {
        val req = call.parseOrNull<AdminService.SetEnrollmentSubjectsRequest>()
            ?: return badRequest()

        try {
            @OptIn(Transactional::class)
            enrollmentService.setSubjects(enrollmentId, req.subject_ids)

            noContent()
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.ENROLLMENT -> notFound("Enrollment not found")
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

            get<Admin.Subjects.Id.EnrollmentIds> { params -> handleGetSubjectEnrollmentIds(params.parent.id) }
        }
    }

    private suspend fun RoutingContext.handleGetSubjects() {
        val subjects = transaction {
            subjectService.getAll().map { it.toProto(withDescription = false, withTeachers = true) }
        }
        call.respond(EnrollmentsService.ListSubjectsResponse(subjects = subjects))
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
        if (subject.teachers.isNotEmpty()) return badRequest("Can't add teachers into a subject immediately")

        try {
            @OptIn(Transactional::class)
            subjectService.create(
                id = subject.id,
                name = subject.name,
                description = subject.description,
                code = subject.code,
                tag = subject.tag,
                location = subject.location,
                capacity = subject.capacity,
                group = subject.group_id,
                thumbnailUrl = subject.thumbnail_url,
                imageUrl = subject.image_url,
            )

            noContent()
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.GROUP -> badRequest("Group not found")
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
            noContent()
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
            name = req.name,
            tag = req.tag,
            capacity = req.capacity,
            teacherIds = if (req.patch_teachers) req.teachers else null,
            enrollmentId = req.enrollment_id,
            description = req.description,
            code = req.code,
            location = req.location,
            group = req.group_id,
            thumbnailUrl = req.thumbnail_url,
            imageUrl = req.image_url,
            setCode = req.patch_code,
            setGroup = req.patch_group_id,
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
                ExceptionEntity.GROUP -> badRequest("Group not found")

                else -> throw e
            }
        } catch (_: NothingToUpdateException) {
            badRequest("Nothing to update")
        }
    }

    private suspend fun RoutingContext.handleGetSubjectEnrollmentIds(id: Int) {
        val ids = transaction { subjectService.getEnrollmentIds(id) }
            ?: return notFound()

        call.respond(AdminService.SubjectEnrollmentIds(enrollment_ids = ids))
    }
}

class AdminGroupsController(
    private val groupService: GroupService,
) : Controller {
    override fun Application.register() {
        adminRoutes {
            get<Admin.Groups> { handleGetGroups() }

            get<Admin.Groups.Id> { params -> handleGetGroup(params.id) }

            put<Admin.Groups.Id> { params -> handlePutGroup(params.id) }

            delete<Admin.Groups.Id> { params -> handleDeleteGroup(params.id) }

            patch<Admin.Groups.Id> { params -> handlePatchGroup(params.id) }

            get<Admin.Groups.MemberCounts> { handleGetGroupMemberCounts() }

            get<Admin.Groups.Id.Managers> { params ->
                val (users, total) = transaction {
                    val (teachers, count) =
                        @OptIn(Transactional::class)
                        groupService.getManagers(params.parent.id, params.page, params.query.ifBlank { null })

                    teachers.map { it.toProto() } to count.toInt()
                }

                call.respond(AdminService.ListUsersResponse(users = users, total = total))
            }

            get<Admin.Groups.Id.Members> { params ->
                handleGetGroupMembers(
                    params.parent.id,
                    params.page,
                    params.query.ifBlank { null })
            }

            delete<Admin.Groups.Id.Members> { params ->
                handleDeleteGroupMembers(params.parent.id)
            }

            post<Admin.Groups.Id.Members.Migrate> { params ->
                handleMigrateGroupMembers(params.parent.parent.id, params.targetGroupId)
            }
        }
    }

    private suspend fun RoutingContext.handleDeleteGroupMembers(groupId: Int) {
        try {
            @OptIn(Transactional::class)
            transaction {
                groupService.deleteMembers(groupId)
            }

            noContent()
        } catch (_: EntityNotFoundException) {
            notFound("Group not found")
        }
    }

    private suspend fun RoutingContext.handleMigrateGroupMembers(groupId: Int, targetGroupId: Int) {
        try {
            @OptIn(Transactional::class)
            transaction {
                groupService.migrateMembers(groupId, targetGroupId)
            }

            ok()
        } catch (_: EntityNotFoundException) {
            notFound("Group not found")
        } catch (_: ConflictException) {
            conflict("Target group must be a different group of the same type")
        }
    }

    private suspend fun RoutingContext.handleGetGroupMembers(groupId: Int, page: Int, query: String?) {
        try {
            val response = transaction {
                val (members, count) = @OptIn(Transactional::class) groupService.getMembers(groupId, page, query)

                AdminService.ListUsersResponse(
                    users = members.map { it.toProto() },
                    total = count.toInt(),
                )
            }
            call.respond(response)
        } catch (_: EntityNotFoundException) {
            notFound("Group not found")
        }
    }

    private suspend fun RoutingContext.handleGetGroups() {
        val groups = transaction { groupService.getAll().map { it.toProto() } }
        call.respond(AdminService.ListGroupsResponse(groups = groups))
    }

    private suspend fun RoutingContext.handleGetGroup(id: Int) {
        val response = transaction { groupService.getById(id)?.toProto() }
            ?: return notFound()

        call.respond(response)
    }

    private suspend fun RoutingContext.handlePutGroup(id: Int) {
        val group = call.parseOrNull<Group>()
            ?: return badRequest()

        if (group.id != id) return badRequest("ID in URL does not match body")

        try {
            @OptIn(Transactional::class)
            groupService.create(group.id, group.name, group.type, group.parent_id)
            noContent()
        } catch (_: ConflictException) {
            conflict("Group with the same ID already exists")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private suspend fun RoutingContext.handleDeleteGroup(id: Int) {
        try {
            @OptIn(Transactional::class)
            groupService.delete(id)
            noContent()
        } catch (_: EntityNotFoundException) {
            notFound("Group not found")
        } catch (_: ConflictException) {
            conflict("Group has members; reassign or remove them before deleting")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private suspend fun RoutingContext.handlePatchGroup(id: Int) {
        val req = call.parseOrNull<AdminService.GroupPatch>()
            ?: return badRequest()

        val update = GroupService.GroupUpdate(
            name = req.name,
            parentId = req.parent_id,
            setParentId = req.patch_parent_id,
        )

        try {
            val proto = transaction {
                @OptIn(Transactional::class)
                groupService.update(id, update).toProto()
            }
            call.respond(proto)
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.GROUP -> notFound("Group not found")

                else -> throw e
            }
        } catch (_: NothingToUpdateException) {
            badRequest("Nothing to update")
        }
    }

    private suspend fun RoutingContext.handleGetGroupMemberCounts() {
        val counts = transaction { groupService.getMemberCounts() }
        call.respond(AdminService.GroupMemberCounts(member_counts = counts))
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
        class Id(val parent: Users, val id: Int) {
            // PUT: SetStudentSelectionsRequest
            @Resource("selections")
            class Selections(val parent: Id)
        }
    }

    @Resource("enrollments")
    class Enrollments(val parent: Admin) {
        // GET: ListEnrollmentsEnrolledCounts
        @Resource("progress")
        class Progress(val parent: Enrollments, val ids: String)

        // PUT: Enrollment, DELETE, PATCH: EnrollmentPatch
        @Resource("{id}")
        class Id(val parent: Enrollments, val id: Int) {
            // GET, PUT
            @Resource("subjects")
            class Subjects(val parent: Id)
        }
    }

    // GET: EnrollmentsService.ListSubjectsResponse
    @Resource("subjects")
    class Subjects(val parent: Admin) {
        // PUT: Subject, GET: Subject, DELETE, PATCH: SubjectPatch
        @Resource("{id}")
        class Id(val parent: Subjects, val id: Int) {
            @Resource("enrollment-ids")
            class EnrollmentIds(val parent: Id)
        }
    }

    // GET: ListGroupsResponse
    @Resource("groups")
    class Groups(val parent: Admin) {
        // PUT: Group, GET: Group, DELETE, PATCH: GroupPatch
        @Resource("{id}")
        class Id(val parent: Groups, val id: Int) {
            // GET: ListUsersResponse
            @Resource("managers")
            class Managers(val parent: Id, val page: Int = 1, val query: String = "")

            // GET: ListUsersResponse, DELETE
            @Resource("members")
            class Members(val parent: Id, val page: Int = 1, val query: String = "") {
                // POST
                @Resource("migrate")
                class Migrate(val parent: Members, @SerialName("target_group_id") val targetGroupId: Int)
            }
        }

        // GET: GroupMemberCounts
        @Resource("member-counts")
        class MemberCounts(val parent: Groups)
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
