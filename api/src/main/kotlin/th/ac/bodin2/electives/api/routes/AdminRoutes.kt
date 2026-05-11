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
import th.ac.bodin2.electives.proto.api.AdminServiceKt.challengeResponse
import th.ac.bodin2.electives.proto.api.AdminServiceKt.groupMemberCounts
import th.ac.bodin2.electives.proto.api.AdminServiceKt.listEnrollmentsEnrolledCounts
import th.ac.bodin2.electives.proto.api.AdminServiceKt.listGroupsResponse
import th.ac.bodin2.electives.proto.api.AdminServiceKt.listUsersResponse
import th.ac.bodin2.electives.proto.api.AdminServiceKt.subjectEnrollmentIds
import th.ac.bodin2.electives.proto.api.AuthServiceKt.authenticateResponse
import th.ac.bodin2.electives.proto.api.EnrollmentsServiceKt.listSubjectsResponse
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import th.ac.bodin2.electives.proto.api.AdminServiceKt.ListEnrollmentsEnrolledCountsKt.counts as enrollmentCounts

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
                call.respond(challengeResponse {
                    challenge = adminAuthService.newChallenge()
                })
            }

            post<Admin.Auth> {
                val req = call.parseOrNull<AuthService.AuthenticateRequest>() ?: return@post notFound()

                try {
                    when (val result = adminAuthService.createSession(
                        id = req.id,
                        signature = req.password,
                        aud = req.clientName,
                        ip = call.request.origin.remoteAddress,
                    )) {
                        is CreateSessionResult.Success -> {
                            call.respond(authenticateResponse {
                                token = result.token
                            })
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
                call.respond(listUsersResponse {
                    transaction {
                        val (students, count) =
                            @OptIn(Transactional::class)
                            usersService.getStudents(params.page, params.query.ifBlank { null })

                        users += students.map { it.toProto() }
                        total = count.toInt()
                    }
                })
            }

            get<Admin.Users.Teachers> { params ->
                call.respond(listUsersResponse {
                    transaction {
                        val (teachers, count) =
                            @OptIn(Transactional::class)
                            usersService.getTeachers(params.page, params.query.ifBlank { null })

                        users += teachers.map { it.toProto() }
                        total = count.toInt()
                    }
                })
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
        val user = req.user
        if (user.id != id) return badRequest("ID in URL does not match body")

        try {
            val protoOrNull = transaction {
                val created = when (user.type) {
                    UserType.STUDENT -> {
                        // Students require fixed GRADE and ROOM group IDs to be set. PROGRAM is optional
                        if (!req.hasGradeId() || !req.hasRoomId()) {
                            return@transaction null
                        }

                        usersService.createStudent(
                            id = user.id,
                            firstName = user.firstName,
                            prefix = if (user.hasPrefix()) user.prefix else null,
                            middleName = if (user.hasMiddleName()) user.middleName else null,
                            lastName = if (user.hasLastName()) user.lastName else null,
                            password = req.password,
                            avatarUrl = if (user.hasAvatarUrl()) user.avatarUrl else null,
                            gradeId = req.gradeId,
                            roomId = req.roomId,
                            programId = if (req.hasProgramId()) req.programId else null,
                            groupIds = req.groupIdsList.ifEmpty { null }
                        )
                    }

                    UserType.TEACHER -> usersService.createTeacher(
                        id = user.id,
                        firstName = user.firstName,
                        prefix = if (user.hasPrefix()) user.prefix else null,
                        middleName = if (user.hasMiddleName()) user.middleName else null,
                        lastName = if (user.hasLastName()) user.lastName else null,
                        password = req.password,
                        avatarUrl = if (user.hasAvatarUrl()) user.avatarUrl else null
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
            call.respond(protoOrNull)
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
                    firstName = if (req.hasFirstName()) req.firstName else null,
                    prefix = if (req.hasPrefix()) req.prefix else null,
                    middleName = if (req.hasMiddleName()) req.middleName else null,
                    lastName = if (req.hasLastName()) req.lastName else null,
                    avatarUrl = if (req.hasAvatarUrl()) req.avatarUrl else null,
                    setPrefix = req.patchPrefix,
                    setMiddleName = req.patchMiddleName,
                    setLastName = req.patchLastName,
                    setAvatarUrl = req.patchAvatarUrl,
                )

                @OptIn(Transactional::class)
                val proto = when (type) {
                    UserType.STUDENT -> usersService.updateStudent(
                        id,
                        UsersService.StudentUpdate(
                            update,
                            groups = if (req.patchGroups) req.groupsList else null,
                            gradeId = if (req.hasGradeId()) req.gradeId else null,
                            roomId = if (req.hasRoomId()) req.roomId else null,
                            programId = if (req.hasProgramId()) req.programId else null,
                            setProgramId = req.patchProgramId,
                        )
                    ).toProto()

                    UserType.TEACHER -> usersService.updateTeacher(id, UsersService.TeacherUpdate(update)).toProto()

                    else -> throw IllegalStateException("Unreachable case: $type")
                }

                if (req.hasNewPassword()) {
                    @OptIn(Transactional::class)
                    usersService.setPassword(id, req.newPassword)
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
            prefix = if (user.hasPrefix()) user.prefix else null,
            middleName = if (user.hasMiddleName()) user.middleName else null,
            lastName = if (user.hasLastName()) user.lastName else null,
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
            if (!it.hasGradeId() || !it.hasRoomId()) {
                return badRequest("Student ${it.user.id} is missing one of grade_id, room_id")
            }

            UsersService.StudentInsert(
                user = it.toUserInsert(),
                gradeId = it.gradeId,
                roomId = it.roomId,
                programId = if (it.hasProgramId()) it.programId else null,
                groups = it.groupIdsList,
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
            usersService.deleteUsers(req.userIdsList)
            ok()
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
            enrollmentSelectionService.forceSetAllStudentSelections(id, req.selectionsMap)

            ok()
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
                group = if (enrollment.hasGroupId()) enrollment.groupId else null,
                startDate = if (enrollment.hasStartDate()) enrollment.startDate.secondsToUTCDateTime else null,
                endDate = if (enrollment.hasEndDate()) enrollment.endDate.secondsToUTCDateTime else null
            )

            ok()
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
            ok()
        } catch (_: EntityNotFoundException) {
            notFound("Enrollment not found")
        } catch (e: ExposedSQLException) {
            badRequest(e.message ?: "SQL exception occurred")
        }
    }

    private suspend fun RoutingContext.handleGetEnrollmentsProgress(idsParam: String) {
        val ids = idsParam.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (ids.isEmpty()) return badRequest()

        call.respond(listEnrollmentsEnrolledCounts {
            transaction {
                val totalStudents by lazy { Students.selectAll().count().toInt() }

                for (enrollmentId in ids) {
                    val enrollment = enrollmentService.getById(enrollmentId) ?: continue
                    val enrolledCount = enrollmentService.getEnrolledCount(enrollmentId)
                    val groupId = enrollment.groupId
                    val total = if (groupId != null) {
                        groupService.getMemberCount(groupId.value)
                    } else {
                        totalStudents
                    }

                    counts[enrollmentId] = enrollmentCounts {
                        this.selected = enrolledCount
                        this.total = total
                    }
                }
            }
        })
    }

    private suspend fun RoutingContext.handlePatchEnrollment(id: Int) {
        val req = call.parseOrNull<AdminService.EnrollmentPatch>()
            ?: return badRequest()

        val update = EnrollmentService.EnrollmentUpdate(
            name = if (req.hasName()) req.name else null,
            group = if (req.hasGroupId()) req.groupId else null,
            startDate = if (req.hasStartDate()) req.startDate.secondsToUTCDateTime else null,
            endDate = if (req.hasEndDate()) req.endDate.secondsToUTCDateTime else null,
            setGroup = req.patchGroupId,
            setStartDate = req.patchStartDate,
            setEndDate = req.patchEndDate,
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
            enrollmentService.setSubjects(enrollmentId, req.subjectIdsList)

            ok()
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
        if (subject.teachersCount > 0) return badRequest("Can't add teachers into a subject immediately")

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
                group = if (subject.hasGroupId()) subject.groupId else null,
                thumbnailUrl = if (subject.hasThumbnailUrl()) subject.thumbnailUrl else null,
                imageUrl = if (subject.hasImageUrl()) subject.imageUrl else null,
            )

            ok()
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
            enrollmentId = if (req.hasEnrollmentId()) req.enrollmentId else null,
            description = if (req.hasDescription()) req.description else null,
            code = if (req.hasCode()) req.code else null,
            location = if (req.hasLocation()) req.location else null,
            group = if (req.hasGroupId()) req.groupId else null,
            thumbnailUrl = if (req.hasThumbnailUrl()) req.thumbnailUrl else null,
            imageUrl = if (req.hasImageUrl()) req.imageUrl else null,
            setCode = req.patchCode,
            setGroup = req.patchGroupId,
            setLocation = req.patchLocation,
            setImageUrl = req.patchImageUrl,
            setDescription = req.patchDescription,
            setThumbnailUrl = req.patchThumbnailUrl,
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

        call.respond(subjectEnrollmentIds {
            enrollmentIds.addAll(ids)
        })
    }
}

class AdminGroupsController(private val groupService: GroupService) : Controller {
    override fun Application.register() {
        adminRoutes {
            get<Admin.Groups> { handleGetGroups() }

            get<Admin.Groups.Id> { params -> handleGetGroup(params.id) }

            put<Admin.Groups.Id> { params -> handlePutGroup(params.id) }

            delete<Admin.Groups.Id> { params -> handleDeleteGroup(params.id) }

            patch<Admin.Groups.Id> { params -> handlePatchGroup(params.id) }

            get<Admin.Groups.MemberCounts> { handleGetGroupMemberCounts() }

            get<Admin.Groups.Id.Members> { params ->
                handleGetGroupMembers(
                    params.parent.id,
                    params.page,
                    params.query.ifBlank { null })
            }
        }
    }

    private suspend fun RoutingContext.handleGetGroupMembers(groupId: Int, page: Int, query: String?) {
        try {
            call.respond(transaction {
                val (members, count) = @OptIn(Transactional::class) groupService.getMembers(groupId, page, query)

                listUsersResponse {
                    users += members.map { it.toProto() }
                    total = count.toInt()
                }
            })
        } catch (_: EntityNotFoundException) {
            notFound("Group not found")
        }
    }

    private suspend fun RoutingContext.handleGetGroups() {
        call.respond(listGroupsResponse {
            transaction {
                groups += groupService.getAll().map { it.toProto() }
            }
        })
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
            groupService.create(group.id, group.name, group.type)
            ok()
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
            ok()
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
            name = if (req.hasName()) req.name else null,
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
        call.respond(groupMemberCounts {
            val counts = transaction { groupService.getMemberCounts() }
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
            @Resource("members")
            class Members(val parent: Id, val page: Int = 1, val query: String = "")
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
