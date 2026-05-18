package th.ac.bodin2.electives.api.services

import io.ktor.server.plugins.di.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.dao.load
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.UsersServiceImpl.Config
import th.ac.bodin2.electives.db.*
import th.ac.bodin2.electives.db.models.*
import th.ac.bodin2.electives.proto.api.GroupType
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.utils.Argon2
import th.ac.bodin2.electives.utils.env
import th.ac.bodin2.electives.utils.withMinimumDelay
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun DependencyRegistry.provideUsersService() = provide<UsersService> {
    UsersServiceImpl(
        Config(
            sessionDurationSeconds =
                (env("USER_SESSION_DURATION")?.toIntOrNull()?.seconds ?: 1.days).inWholeSeconds,
            minimumSessionCreationTime =
                (env("USER_SESSION_CREATION_MINIMUM_TIME")?.toIntOrNull()?.milliseconds ?: 500.milliseconds),
        ),
        argon2 = resolve<Argon2>()
    )
}

fun userSearchCondition(query: String): Op<Boolean> {
    val pattern = "%$query%"
    return (Users.firstName like pattern) or
            (Users.middleName like pattern) or
            (Users.lastName like pattern) or
            (Users.id.castTo(VarCharColumnType(255)) like pattern)
}

class UsersServiceImpl(val config: Config, val argon2: Argon2) : UsersService {
    class Config(val sessionDurationSeconds: Long, val minimumSessionCreationTime: Duration)

    private val _sessionCreationFlow = MutableSharedFlow<Int>()
    override val sessionCreationFlow: SharedFlow<Int> = _sessionCreationFlow.asSharedFlow()

    companion object {
        private const val PAGE_SIZE = 50
        private const val TOKEN_SIZE = 32
        private val secureRand = SecureRandom()
        private val logger = LoggerFactory.getLogger(UsersServiceImpl::class.java)

        private val userInfoFields = listOf(
            Users.id,
            Users.avatarUrl,
            Users.prefix,
            Users.firstName,
            Users.middleName,
            Users.lastName
        )
    }

    override fun getUserType(id: Int): UserType {
        val query = Users
            .leftJoin(Students)
            .leftJoin(Teachers)
            .leftJoin(Admins)
            .select(Users.id, Students.id, Teachers.id, Admins.id)
            .where { Users.id eq id }
            .firstOrNull()

        query ?: throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")

        return when {
            query.getOrNull(Students.id) != null -> UserType.STUDENT
            query.getOrNull(Teachers.id) != null -> UserType.TEACHER
            query.getOrNull(Admins.id) != null -> UserType.ADMIN
            else -> throw IllegalStateException("User is not a Student, Teacher, or Admin: $id")
        }
    }

    override fun createStudent(
        id: Int,
        firstName: String,
        gradeId: Int,
        roomId: Int,
        programId: Int?,
        prefix: String?,
        middleName: String?,
        lastName: String?,
        password: String,
        avatarUrl: String?,
        groupIds: List<Int>?,
    ): Student {
        assertGroupHasType(gradeId, GroupType.GRADE)
        assertGroupHasType(roomId, GroupType.ROOM)
        programId?.let { assertGroupHasType(it, GroupType.PROGRAM) }
        assertGroupsHaveType(groupIds.orEmpty(), GroupType.CUSTOM)

        val user = createUser(id, firstName, prefix, middleName, lastName, password, avatarUrl)
        val studentRow = Students
            .insert { it[Students.id] = user.id }
            .resultedValues!!
            .first()

        // All memberships go into the join table: GRADE + ROOM (always) + PROGRAM (optional) + any CUSTOMs
        val allGroupIds = (listOfNotNull(gradeId, roomId, programId) + groupIds.orEmpty()).distinct()
        StudentGroups.batchInsert(allGroupIds) {
            this[StudentGroups.student] = id
            this[StudentGroups.group] = it
        }

        return Student.wrapRow(studentRow).load(Student::user, Student::groups)
    }

    /**
     * Asserts that the given group exists and has the expected [GroupType].
     *
     * @throws EntityNotFoundException if the group does not exist.
     * @throws IllegalArgumentException if the group has a different type.
     */
    private fun assertGroupHasType(groupId: Int, expected: GroupType) {
        val type = Group.getType(groupId)
            ?: throw EntityNotFoundException(ExceptionEntity.GROUP, "Group does not exist: $groupId")

        require(type == expected.value) {
            "Group $groupId has type: ${GroupType.fromValue(type)?.name ?: type}, expected: ${expected.name}"
        }
    }

    /**
     * Asserts that every given group exists and has the expected [GroupType].
     * @throws UsersService.BatchOperationException.MissingGroups if any group does not exist.
     * @throws IllegalArgumentException if any group has a different type.
     */
    private fun assertGroupsHaveType(groupIds: Collection<Int>, expected: GroupType) {
        if (groupIds.isEmpty()) return

        val distinct = groupIds.toSet()
        val rows = Groups.select(Groups.id, Groups.type)
            .where { Groups.id inList distinct }
            .associate { it[Groups.id].value to it[Groups.type] }

        val missing = distinct.filter { it !in rows }
        if (missing.isNotEmpty()) {
            throw UsersService.BatchOperationException.MissingGroups(missing)
        }

        val mismatched = rows.filter { it.value != expected.value }.keys
        require(mismatched.isEmpty()) {
            "Groups $mismatched don't have the expected type: ${expected.name}"
        }
    }

    @Transactional
    override fun createStudents(inserts: List<UsersService.StudentInsert>) = transaction {
        if (inserts.isEmpty()) return@transaction emptyList()

        // Collect every referenced group ID and type so we can validate in one query
        val expectedTypes = mutableMapOf<Int, GroupType>()
        for (insert in inserts) {
            fun add(id: Int, type: GroupType) {
                val prev = expectedTypes[id]
                if (prev != null && prev != type) {
                    throw UsersService.BatchOperationException.InvalidUserData(
                        insert.user.id,
                        IllegalArgumentException("Group $id is referenced as both ${prev.name} and ${type.name}")
                    )
                }
                expectedTypes[id] = type
            }
            add(insert.gradeId, GroupType.GRADE)
            add(insert.roomId, GroupType.ROOM)
            insert.programId?.let { add(it, GroupType.PROGRAM) }
            insert.groups.forEach { add(it, GroupType.CUSTOM) }
        }

        val existing = Groups.select(Groups.id, Groups.type)
            .where { Groups.id inList expectedTypes.keys.toList() }
            .associate { it[Groups.id].value to it[Groups.type] }

        val missing = expectedTypes.keys.filter { it !in existing }
        if (missing.isNotEmpty()) throw UsersService.BatchOperationException.MissingGroups(missing)

        val mismatched = expectedTypes.filter { (id, type) -> existing[id] != type.value }
        if (mismatched.isNotEmpty()) {
            // Attribute the error to the first student that referenced any mismatched group.
            val badId = mismatched.keys.first()
            val owner = inserts.first { insert ->
                badId == insert.gradeId || badId == insert.roomId || badId == insert.programId ||
                        badId in insert.groups
            }

            throw UsersService.BatchOperationException.InvalidUserData(
                owner.user.id,
                IllegalArgumentException("Group $badId has wrong type for its slot")
            )
        }

        val prepared = insertUserBatch(inserts)

        Students.batchInsert(prepared) { item ->
            this[Students.id] = item.request.user.id
        }

        val memberships = prepared.flatMap { item ->
            val insert = item.request
            val ids = (listOfNotNull(insert.gradeId, insert.roomId, insert.programId) + insert.groups).distinct()
            ids.map { groupId -> insert.user.id to groupId }
        }

        StudentGroups.batchInsert(memberships) { (studentId, groupId) ->
            this[StudentGroups.student] = studentId
            this[StudentGroups.group] = groupId
        }

        return@transaction Student.find { Students.id inList prepared.map { it.request.user.id } }
            .with(Student::user, Student::groups)
            .toList()
    }

    private data class PreparedUserInsert<T>(
        val request: T,
        val passwordHash: String
    )

    override fun createTeacher(
        id: Int,
        firstName: String,
        prefix: String?,
        middleName: String?,
        lastName: String?,
        password: String,
        avatarUrl: String?,
        groupIds: List<Int>?,
    ): Teacher {
        // Validate all groups exist. Teachers don't have "slotted" groups (like grade/room),
        // so we just check for existence.
        groupIds?.let { ids ->
            if (ids.isNotEmpty()) {
                val distinct = ids.toSet()
                val existing = Groups.select(Groups.id)
                    .where { Groups.id inList distinct }
                    .map { it[Groups.id].value }
                    .toSet()

                val missing = distinct.filter { it !in existing }
                if (missing.isNotEmpty()) {
                    throw EntityNotFoundException(ExceptionEntity.GROUP, "Groups do not exist: $missing")
                }
            }
        }

        val user = createUser(id, firstName, prefix, middleName, lastName, password, avatarUrl)
        val teacher = Teacher.new(user.id.value) {}

        groupIds?.let { ids ->
            TeacherGroups.batchInsert(ids.distinct()) {
                this[TeacherGroups.teacher] = id
                this[TeacherGroups.group] = it
            }
        }

        return teacher.load(Teacher::user, Teacher::groups)
    }

    @Transactional
    override fun createTeachers(inserts: List<UsersService.TeacherInsert>) = transaction {
        if (inserts.isEmpty()) return@transaction emptyList()

        // Validate groups
        val allGroupIds = inserts.flatMap { it.groups }.distinct()
        if (allGroupIds.isNotEmpty()) {
            val existing = Groups.select(Groups.id)
                .where { Groups.id inList allGroupIds }
                .map { it[Groups.id].value }
                .toSet()

            val missing = allGroupIds.filter { it !in existing }
            if (missing.isNotEmpty()) throw UsersService.BatchOperationException.MissingGroups(missing)
        }

        val prepared = insertUserBatch(inserts)

        Teachers.batchInsert(prepared) { item ->
            val uid = item.request.user.id

            this[Teachers.id] = uid
        }

        val memberships = prepared.flatMap { item ->
            item.request.groups.distinct().map { groupId -> item.request.user.id to groupId }
        }

        TeacherGroups.batchInsert(memberships) { (teacherId, groupId) ->
            this[TeacherGroups.teacher] = teacherId
            this[TeacherGroups.group] = groupId
        }

        return@transaction Teacher.find { Teachers.id inList prepared.map { it.request.user.id } }
            .with(Teacher::user, Teacher::groups)
            .toList()
    }

    // @TODO: Test this
    @Transactional
    override fun createAdmin(insert: UsersService.AdminInsert) = transaction {
        val user = createUser(
            id = insert.user.id,
            firstName = insert.user.firstName,
            prefix = insert.user.prefix,
            middleName = insert.user.middleName,
            lastName = insert.user.lastName,
            password = insert.user.password,
            avatarUrl = insert.user.avatarUrl,
            assertPassword = false
        )

        Admin.new(user.id.value) {
            publicKey = Base64.getEncoder().encodeToString(insert.publicKey.encoded)
        }
    }

    private fun <T : UsersService.UserInsert> insertUserBatch(inserts: List<T>): List<PreparedUserInsert<T>> {
        val conflicts = Users
            .select(Users.id)
            .where { Users.id inList inserts.map { it.user.id } }
            .map { it[Users.id].value }
            .toList()

        if (conflicts.isNotEmpty()) {
            throw UsersService.BatchOperationException.ConflictingEntities(conflicts)
        }

        val prepared = inserts.map { req ->
            val user = req.user
            val passwordHash = try {
                argon2.hash(user.password.assertPasswordRequirements().toCharArray())
            } catch (e: IllegalArgumentException) {
                throw UsersService.BatchOperationException.InvalidUserData(user.id, e)
            }

            PreparedUserInsert(
                request = req,
                passwordHash = passwordHash
            )
        }

        Users.batchInsert(prepared) { item ->
            val user = item.request.user

            this[Users.id] = user.id
            this[Users.avatarUrl] = user.avatarUrl
            this[Users.prefix] = user.prefix
            this[Users.firstName] = user.firstName
            this[Users.middleName] = user.middleName
            this[Users.lastName] = user.lastName
            this[Users.passwordHash] = item.passwordHash
        }

        return prepared
    }

    @Transactional
    override fun deleteUser(id: Int) {
        val rows = transaction { Users.deleteWhere { Users.id eq id } }
        if (rows == 0) {
            throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")
        }

        logger.info("Deleted user: $id")
    }

    @Transactional
    override suspend fun deleteUsers(id: List<Int>) {
        if (id.isEmpty()) return

        suspendTransaction {
            val notFoundIds = Users
                .select(Users.id)
                .where { not(Users.id inList id) }
                .map { it[Users.id].value }
                .toList()

            if (notFoundIds.isNotEmpty()) {
                throw UsersService.BatchOperationException.NotFoundEntities(notFoundIds)
            }

            Users.deleteWhere { Users.id inList id }
        }

        logger.info("Deleted users: ${id.joinToString(", ")}")
    }

    @Transactional
    override fun updateStudent(id: Int, update: UsersService.StudentUpdate) = transaction {
        Student.assertExists(id)

        // Validate group types up-front so we never partially update on failure
        update.gradeId?.let { assertGroupHasType(it, GroupType.GRADE) }
        update.roomId?.let { assertGroupHasType(it, GroupType.ROOM) }
        if (update.setProgramId) update.programId?.let { assertGroupHasType(it, GroupType.PROGRAM) }
        update.groups?.let { assertGroupsHaveType(it, GroupType.CUSTOM) }

        val hasFixedGroupUpdate = update.gradeId != null || update.roomId != null || update.setProgramId

        try {
            updateUser(id, update.user)
        } catch (e: NothingToUpdateException) {
            if (update.groups == null && !hasFixedGroupUpdate) throw e
        }

        // Removes the student's current membership in the fixed slot of [type], if any
        fun clearFixedSlot(type: GroupType) {
            val currentOfType =
                (StudentGroups innerJoin Groups)
                    .select(StudentGroups.group)
                    .where { (StudentGroups.student eq id) and (Groups.type eq type.value) }
                    .map { it[StudentGroups.group].value }

            if (currentOfType.isNotEmpty()) {
                StudentGroups.deleteWhere {
                    (StudentGroups.student eq id) and (StudentGroups.group inList currentOfType)
                }
            }
        }

        // For each slotted group being updated, swap the existing membership of that type for the new one
        fun swapFixedSlot(newGroupId: Int, type: GroupType) {
            clearFixedSlot(type)
            StudentGroups.insert {
                it[StudentGroups.student] = id
                it[StudentGroups.group] = newGroupId
            }
        }

        update.gradeId?.let { swapFixedSlot(it, GroupType.GRADE) }
        update.roomId?.let { swapFixedSlot(it, GroupType.ROOM) }
        if (update.setProgramId) {
            // Either swap to the new program or clear the existing one (programId == null).
            update.programId?.let { swapFixedSlot(it, GroupType.PROGRAM) } ?: clearFixedSlot(GroupType.PROGRAM)
        }

        // Replace all CUSTOM memberships for this student with the provided list
        update.groups?.let { newCustoms ->
            val currentCustoms =
                (StudentGroups innerJoin Groups)
                    .select(StudentGroups.group)
                    .where { (StudentGroups.student eq id) and (Groups.type eq GroupType.CUSTOM.value) }
                    .map { it[StudentGroups.group].value }

            if (currentCustoms.isNotEmpty()) {
                StudentGroups.deleteWhere {
                    (StudentGroups.student eq id) and (StudentGroups.group inList currentCustoms)
                }
            }

            StudentGroups.batchInsert(newCustoms.distinct()) {
                this[StudentGroups.student] = id
                this[StudentGroups.group] = it
            }
        }

        Student.findById(id)!!.load(Student::user, Student::groups)
    }

    @Transactional
    override fun updateTeacher(id: Int, update: UsersService.TeacherUpdate) = transaction {
        Teacher.assertExists(id)

        // Validate all referenced groups exist up-front so we never partially update on failure.
        update.groups?.let { groupIds ->
            if (groupIds.isNotEmpty()) {
                val distinct = groupIds.toSet()
                val existing = Groups.select(Groups.id)
                    .where { Groups.id inList distinct }
                    .map { it[Groups.id].value }
                    .toSet()

                val missing = distinct.filter { it !in existing }
                if (missing.isNotEmpty()) {
                    throw UsersService.BatchOperationException.MissingGroups(missing)
                }
            }
        }

        try {
            updateUser(id, update.user)
        } catch (e: NothingToUpdateException) {
            if (update.groups == null) throw e
        }

        // Replace all of the teacher's group memberships with the provided list.
        update.groups?.let { newGroups ->
            TeacherGroups.deleteWhere { TeacherGroups.teacher eq id }
            TeacherGroups.batchInsert(newGroups.distinct()) {
                this[TeacherGroups.teacher] = id
                this[TeacherGroups.group] = it
            }
        }

        Teacher.findById(id)!!.load(Teacher::user, Teacher::groups)
    }

    private fun updateUser(id: Int, update: UsersService.UserUpdate) {
        Users.update(where = { Users.id eq id }) {
            with(update) {
                if (firstName != null) it[Users.firstName] = firstName
                if (setPrefix) it[Users.prefix] = prefix
                if (setMiddleName) it[Users.middleName] = middleName
                if (setLastName) it[Users.lastName] = lastName
                if (setAvatarUrl) it[Users.avatarUrl] = avatarUrl
            }

            if (it.firstDataSet.isEmpty()) throw NothingToUpdateException()
        }
    }

    @Transactional
    override fun setPassword(id: Int, newPassword: String) {
        val password = newPassword.assertPasswordRequirements()

        val rows = transaction {
            Users.update(where = { Users.id eq id }) {
                it[Users.passwordHash] = argon2.hash(password.toCharArray())
            }
        }

        if (rows == 0) throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")
    }

    override fun getTeacherById(id: Int): Teacher? = Teacher.findById(id)?.load(Teacher::user, Teacher::groups)

    override fun getStudentById(id: Int): Student? = Student.findById(id)?.load(Student::user, Student::groups)

    override fun getAdminById(id: Int): Admin? = Admin.findById(id)

    @Transactional
    override fun getStudents(page: Int, query: String?): Pair<List<Student>, Long> {
        require(page >= 1) { "Page must be at least 1" }
        val offset = ((page - 1) * PAGE_SIZE).toLong()
        val searchCondition = query?.takeIf { it.isNotBlank() }?.let { userSearchCondition(it) }
        return transaction {
            val count = if (searchCondition != null) {
                Students.innerJoin(Users).selectAll().where(searchCondition).count()
            } else {
                Students.selectAll().count()
            }

            val dataQuery = Students.innerJoin(Users).selectAll().apply {
                if (searchCondition != null) where(searchCondition)
            }.orderBy(Students.id).limit(PAGE_SIZE).offset(offset)

            val students = Student.wrapRows(dataQuery)
                .with(Student::user, Student::groups)
                .toList()

            students to count
        }
    }

    @Transactional
    override fun getTeachers(page: Int, query: String?): Pair<List<Teacher>, Long> {
        require(page >= 1) { "Page must be at least 1" }
        val offset = ((page - 1) * PAGE_SIZE).toLong()
        val searchCondition = query?.takeIf { it.isNotBlank() }?.let { userSearchCondition(it) }
        return transaction {
            val count = if (searchCondition != null) {
                Teachers.innerJoin(Users).selectAll().where(searchCondition).count()
            } else {
                Teachers.selectAll().count()
            }

            val dataQuery = Teachers.innerJoin(Users).selectAll().apply {
                if (searchCondition != null) where(searchCondition)
            }.orderBy(Teachers.id).limit(PAGE_SIZE).offset(offset)

            val teachers = Teacher.wrapRows(dataQuery)
                .with(Teacher::user, Teacher::groups)
                .toList()

            teachers to count
        }
    }

    @Transactional
    override suspend fun createSession(id: Int, password: String, aud: String): String =
        withMinimumDelay(config.minimumSessionCreationTime) {
            val password = password.assertPasswordRequirements()

            val aud = aud.trim().apply {
                require(!isEmpty()) { "Audience blank for user: $id" }
                require(length <= 256) { "Audience string too long for user: $id (aud = ${slice(0..32)}...)" }
            }

            suspendTransaction {
                val user = Users.select(Users.passwordHash).where { Users.id eq id }.singleOrNull()
                    ?: throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")

                val passwordHash = user[Users.passwordHash]

                require(passwordHash != null) { "User not password authenticatable: $id" }

                require(
                    argon2.verify(passwordHash, password.toCharArray())
                ) { "Invalid password for user: $id" }

                val token = insecurelyCreateSessionWithoutValidation(id)

                _sessionCreationFlow.emit(id)
                logger.debug("New session created, user: $id, aud: $aud")

                token
            }
        }

    @Transactional
    override fun insecurelyCreateSessionWithoutValidation(id: Int, customDurationSeconds: Long?): String {
        val session = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(ByteArray(TOKEN_SIZE).apply { secureRand.nextBytes(this) })

        Users.update({ Users.id eq id }) {
            it[Users.sessionExpiry] =
                LocalDateTime.now().plusSeconds(customDurationSeconds ?: config.sessionDurationSeconds)

            it[Users.sessionHash] = argon2.hash(session.toCharArray())
        }

        return "$id.$session"
    }

    override fun getSessionUser(token: String): UsersService.SessionUser {
        val (subject, session) = token.split(".", limit = 2).takeIf { it.size == 2 }
            ?: throw IllegalArgumentException("Invalid session token format")

        val userId = subject.toIntOrNull() ?: throw IllegalArgumentException("Invalid token subject: $subject")

        val user = Users.select(Users.sessionHash, Users.sessionExpiry).where { Users.id eq userId }.singleOrNull()
            ?: throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $userId")

        val sessionExpiry = user[Users.sessionExpiry]
            ?: throw IllegalArgumentException("No active session for user: $userId")

        val sessionHash = user[Users.sessionHash]
            ?: throw IllegalArgumentException("No active session for user: $userId")

        if (sessionExpiry.isBefore(LocalDateTime.now()))
            throw IllegalArgumentException("Session expired for user: $userId")

        if (!argon2.verify(sessionHash, session.toCharArray()))
            throw IllegalArgumentException("Invalid session token for user: $userId")

        logger.debug("Validated session token, user: $userId")

        return UsersService.SessionUser(userId, getUserType(userId))
    }

    override fun clearSession(userId: Int) {
        Users.update({ Users.id eq userId }) {
            it[Users.sessionExpiry] = null
            it[Users.sessionHash] = null
        }

        logger.debug("Session cleared, user: $userId")
    }

    private fun createUser(
        id: Int,
        firstName: String,
        prefix: String? = null,
        middleName: String?,
        lastName: String?,
        password: String,
        avatarUrl: String?,
        assertPassword: Boolean = true
    ): User {
        val password = if (assertPassword) password.assertPasswordRequirements() else password

        val stmt = Users.insertIgnore {
            it[Users.id] = id
            it[Users.prefix] = prefix
            it[Users.firstName] = firstName
            it[Users.middleName] = middleName
            it[Users.lastName] = lastName
            it[Users.passwordHash] = if (password.isBlank()) null else argon2.hash(password.toCharArray())
            it[Users.avatarUrl] = avatarUrl
        }

        if (stmt.insertedCount == 0) throw ConflictException(ExceptionEntity.USER)
        return User.wrapRow(stmt.resultedValues!!.first())
    }

    private fun String.assertPasswordRequirements(): String {
        val pwd = this.trim()

        require(pwd.length >= 4) { "Password must be at least 4 characters once trimmed" }
        require(pwd.length <= 4096) { "Password must have less or equal to 4096 characters" }

        return pwd
    }
}