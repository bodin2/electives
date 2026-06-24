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
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.ADMIN_AUTHENTICATION
import th.ac.bodin2.electives.api.RATE_LIMIT_ADMIN
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.asBadRequest
import th.ac.bodin2.electives.api.services.*
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.*
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
    val enrollmentService: EnrollmentService by dependencies
    val subjectService: SubjectService by dependencies
    val groupService: GroupService by dependencies

    listOf(
        AdminUsersController(usersService),
        AdminEnrollmentsController(enrollmentService, groupService),
        AdminEnrollmentsSubjectsController(enrollmentService),
        AdminSubjectsController(subjectService),
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

class AdminUsersController(
    private val usersService: UsersService,
) : Controller {
    override fun Application.register() {
        adminRoutes {
            put<Admin.Users.Id> { params -> handlePutUser(params.id) }

            patch<Admin.Users.Id> { params -> handlePatchUser(params.id) }

            delete<Admin.Users.Id> { params -> handleDeleteUser(params.id) }

            post<Admin.Users.Bulk> { handleBulkAddUsers() }

            delete<Admin.Users.Bulk> { handleBulkDeleteUsers() }
        }
    }

    private suspend fun RoutingContext.handlePutUser(id: Int) {
        val req = call.parseOrNull<AdminService.AddUserRequest>()
            ?: throw badRequest()
        val user = req.user ?: throw badRequest("Missing user")
        if (user.id != id) throw badRequest("ID in URL does not match body")

        val protoOrNull = try {
            dbQuery {
                val created = when (user.type) {
                    UserType.STUDENT -> {
                        val gradeId = req.grade_id
                        val roomId = req.room_id

                        // Students require fixed GRADE and ROOM group IDs to be set. PROGRAM is optional
                        if (gradeId == null || roomId == null) {
                            return@dbQuery null
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

                    else -> return@dbQuery null
                }

                when (created) {
                    is Student -> created.toProto()
                    is Teacher -> created.toProto()
                    else -> null
                }
            }
        } catch (e: IllegalArgumentException) {
            throw badRequest(e.message ?: "Invalid request")
        } catch (_: EntityNotFoundException) {
            throw badRequest("One or more specified groups not found")
        } catch (_: ConflictException) {
            throw conflict("User with the same ID already exists")
        } catch (e: ExposedSQLException) {
            throw badRequest(e.message ?: "SQL exception occurred")
        }

        protoOrNull
            ?: throw badRequest("Unsupported user type or missing required group IDs (grade_id, room_id)")

        created(protoOrNull)
    }

    private suspend fun RoutingContext.handlePatchUser(id: Int) {
        val req = call.parseOrNull<AdminService.UserPatch>()
            ?: throw badRequest()

        val proto = try {
            dbQuery {
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
        } catch (e: EntityNotFoundException) {
            throw when (e.entity) {
                ExceptionEntity.USER,
                ExceptionEntity.TEACHER,
                ExceptionEntity.STUDENT -> notFound("User not found")

                ExceptionEntity.GROUP -> badRequest("One or more groups not found")

                else -> e
            }
        } catch (e: IllegalArgumentException) {
            if (e is NothingToUpdateException) throw badRequest("Nothing to update")
            throw badRequest(e.message ?: "Invalid request")
        }

        call.respond(proto)
    }

    private suspend fun RoutingContext.handleDeleteUser(id: Int) {
        try {
            @OptIn(Transactional::class)
            usersService.deleteUser(id)
        } catch (_: EntityNotFoundException) {
            throw notFound("User not found")
        } catch (e: ExposedSQLException) {
            throw badRequest(e.message ?: "SQL exception occurred")
        }
        noContent()
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
            ?: throw badRequest()

        if (req.values.any { it.user == null }) throw badRequest("Missing user in one or more entries")

        val inserts = req.values.groupBy { it.user!!.type }

        if (inserts.keys.minus(supportedBulkAddTypes).any { key ->
                (inserts[key]?.isNotEmpty()) ?: false
            }) {
            throw badRequest("Unsupported user types")
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
                throw badRequest("Student ${it.user!!.id} is missing one of grade_id, room_id")
            }

            UsersService.StudentInsert(
                user = it.toUserInsert(),
                gradeId,
                roomId,
                programId = it.program_id,
                groups = it.group_ids,
            )
        }

        val created: List<User> = try {
            // Dedupe transactions
            dbQuery {
                buildList {
                    if (!teacherInserts.isNullOrEmpty()) {
                        usersService.createTeachers(teacherInserts).forEach { add(it.toProto()) }
                    }

                    if (!studentInserts.isNullOrEmpty()) {
                        usersService.createStudents(studentInserts).forEach { add(it.toProto()) }
                    }
                }
            }
        } catch (e: UsersService.BatchOperationException) {
            when (e) {
                is UsersService.BatchOperationException.InvalidUserData -> {
                    val cause = e.cause
                    if (cause is IllegalArgumentException) {
                        throw badRequest("User ${e.id} has invalid data: ${cause.message ?: "unknown"}")
                    }
                    throw e
                }

                is UsersService.BatchOperationException.MissingGroups ->
                    throw badRequest("One or more specified groups not found")

                is UsersService.BatchOperationException.ConflictingEntities ->
                    throw conflict("One or more users with the same ID already exists")

                else -> throw e
            }
        }

        created(AdminService.ListUsersResponse(users = created, total = created.size))
    }

    private suspend fun RoutingContext.handleBulkDeleteUsers() {
        val req = call.parseOrNull<AdminService.BulkDeleteUsersRequest>()
            ?: throw badRequest()

        try {
            @OptIn(Transactional::class)
            usersService.deleteUsers(req.user_ids)
        } catch (e: UsersService.BatchOperationException.NotFoundEntities) {
            throw badRequest("Users not found: ${e.ids.joinToString(", ")}")
        }
        noContent()
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
            ?: throw badRequest()

        if (enrollment.id != id) throw badRequest("ID in URL does not match body")

        try {
            @OptIn(Transactional::class)
            enrollmentService.create(
                id = enrollment.id,
                name = enrollment.name,
                group = enrollment.group_id,
                startDate = enrollment.start_date?.secondsToUTCDateTime,
                endDate = enrollment.end_date?.secondsToUTCDateTime
            )
        } catch (e: EntityNotFoundException) {
            throw e.asBadRequest()
        } catch (_: ConflictException) {
            throw conflict("Enrollment with the same ID already exists")
        } catch (e: ExposedSQLException) {
            throw badRequest(e.message ?: "SQL exception occurred")
        }
        noContent()
    }

    private suspend fun RoutingContext.handleDeleteEnrollment(id: Int) {
        try {
            @OptIn(Transactional::class)
            enrollmentService.delete(id)
        } catch (_: EntityNotFoundException) {
            throw notFound("Enrollment not found")
        } catch (e: ExposedSQLException) {
            throw badRequest(e.message ?: "SQL exception occurred")
        }
        noContent()
    }

    private suspend fun RoutingContext.handleGetEnrollmentsProgress(idsParam: String) {
        val ids = idsParam.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (ids.isEmpty()) throw badRequest()

        val counts = dbQuery {
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
            ?: throw badRequest()

        val update = EnrollmentService.EnrollmentUpdate(
            name = req.name,
            group = req.group_id,
            startDate = req.start_date?.secondsToUTCDateTime,
            endDate = req.end_date?.secondsToUTCDateTime,
            setGroup = req.patch_group_id,
            setStartDate = req.patch_start_date,
            setEndDate = req.patch_end_date,
        )

        val proto = try {
            dbQuery {
                @OptIn(Transactional::class)
                enrollmentService.update(id, update).toProto()
            }
        } catch (e: EntityNotFoundException) {
            throw when (e.entity) {
                ExceptionEntity.ENROLLMENT -> notFound("Enrollment not found")
                ExceptionEntity.GROUP -> badRequest("Group not found")

                else -> e
            }
        } catch (_: NothingToUpdateException) {
            throw badRequest("Nothing to update")
        }
        call.respond(proto)
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
            ?: throw badRequest()

        try {
            @OptIn(Transactional::class)
            enrollmentService.setSubjects(enrollmentId, req.subject_ids)
        } catch (e: EntityNotFoundException) {
            throw when (e.entity) {
                ExceptionEntity.ENROLLMENT -> notFound("Enrollment not found")
                ExceptionEntity.SUBJECT -> badRequest("One or more subjects not found")

                else -> e
            }
        }
        noContent()
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
        val subjects = dbQuery {
            subjectService.getAll().map { it.toProto(withDescription = false, withTeachers = true) }
        }
        call.respond(EnrollmentsService.ListSubjectsResponse(subjects = subjects))
    }

    private suspend fun RoutingContext.handleGetSubject(id: Int) {
        val response = dbQuery { subjectService.getById(id)?.toProto(withDescription = true, withTeachers = true) }
            ?: throw notFound()

        call.respond(response)
    }

    private suspend fun RoutingContext.handlePutSubject(id: Int) {
        val subject = call.parseOrNull<Subject>()
            ?: throw badRequest()

        if (subject.id != id) throw badRequest("ID in URL does not match body")
        if (subject.teachers.isNotEmpty()) throw badRequest("Can't add teachers into a subject immediately")

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
        } catch (e: EntityNotFoundException) {
            throw when (e.entity) {
                ExceptionEntity.GROUP -> e.asBadRequest()
                ExceptionEntity.TEACHER -> badRequest("One or more teachers not found")

                else -> e
            }
        } catch (_: ConflictException) {
            throw conflict("Subject with the same ID already exists")
        } catch (e: ExposedSQLException) {
            throw badRequest(e.message ?: "SQL exception occurred")
        }
        noContent()
    }

    private suspend fun RoutingContext.handleDeleteSubject(id: Int) {
        try {
            @OptIn(Transactional::class)
            subjectService.delete(id)
        } catch (_: EntityNotFoundException) {
            throw notFound("Subject not found")
        } catch (e: ExposedSQLException) {
            throw badRequest(e.message ?: "SQL exception occurred")
        }
        noContent()
    }

    private suspend fun RoutingContext.handlePatchSubject(id: Int) {
        val req = call.parseOrNull<AdminService.SubjectPatch>()
            ?: throw badRequest()

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

        val proto = try {
            dbQuery {
                @OptIn(Transactional::class)
                subjectService.update(id, update).toProto(withDescription = true, withTeachers = true)
            }
        } catch (e: EntityNotFoundException) {
            throw when (e.entity) {
                ExceptionEntity.SUBJECT -> notFound("Subject not found")
                ExceptionEntity.TEACHER -> badRequest("One or more teachers not found")
                ExceptionEntity.GROUP -> e.asBadRequest()

                else -> e
            }
        } catch (_: NothingToUpdateException) {
            throw badRequest("Nothing to update")
        }
        call.respond(proto)
    }

    private suspend fun RoutingContext.handleGetSubjectEnrollmentIds(id: Int) {
        val ids = dbQuery { subjectService.getEnrollmentIds(id) }
            ?: throw notFound()

        call.respond(AdminService.SubjectEnrollmentIds(enrollment_ids = ids))
    }
}

private val Long.secondsToUTCDateTime: LocalDateTime
    get() = Instant.ofEpochSecond(this)
        .atZone(ZoneId.of("UTC"))
        .toLocalDateTime()

@Suppress("UNUSED")
@Resource("/admin")
private class Admin {
    @Resource("users")
    class Users(val parent: Admin) {
        // @TODO: Add route tests
        // POST: BulkAddUsersRequest, DELETE: BulkDeleteUsersRequest
        @Resource("bulk")
        class Bulk(val parent: Users)

        // DELETE, PATCH: UserPatch, PUT: AddUserRequest
        @Resource("{id}")
        class Id(val parent: Users, val id: Int)
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
