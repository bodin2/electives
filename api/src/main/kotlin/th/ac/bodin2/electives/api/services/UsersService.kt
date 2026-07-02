package th.ac.bodin2.electives.api.services

import io.ktor.server.plugins.di.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.dao.load
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.*
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.isAdminEnabled
import th.ac.bodin2.electives.api.utils.dbQuery
import th.ac.bodin2.electives.db.*
import th.ac.bodin2.electives.db.models.*
import th.ac.bodin2.electives.proto.api.GroupType
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.utils.Argon2
import th.ac.bodin2.electives.utils.env
import th.ac.bodin2.electives.utils.withMinimumDelay
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun DependencyRegistry.provideUsersService() = provide<UsersService> {
    UsersService(
        UsersService.Config(
            sessionDurationSeconds =
                (env("USER_SESSION_DURATION")?.toIntOrNull()?.seconds ?: 1.days).inWholeSeconds,
            minimumSessionCreationTime =
                (env("USER_SESSION_CREATION_MINIMUM_TIME")?.toIntOrNull()?.milliseconds ?: 500.milliseconds),
            adminSessionDurationSeconds =
                (env("ADMIN_SESSION_DURATION")?.toIntOrNull()?.seconds ?: 1.hours).inWholeSeconds,
            adminMinimumSessionCreationTime =
                (env("ADMIN_SESSION_CREATION_MINIMUM_TIME")?.toIntOrNull()?.milliseconds ?: 3.seconds),
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

class UsersService(val config: Config, val argon2: Argon2) {
    class Config(
        val sessionDurationSeconds: Long,
        val minimumSessionCreationTime: Duration,
        val adminSessionDurationSeconds: Long = sessionDurationSeconds,
        val adminMinimumSessionCreationTime: Duration = minimumSessionCreationTime,
    )

    private val _sessionCreationFlow = MutableSharedFlow<Int>()
    val sessionCreationFlow: SharedFlow<Int> = _sessionCreationFlow.asSharedFlow()

    init {
        bootstrapAdminReset()
    }

    /**
     * If `ADMIN_ENABLED` is set and `ADMIN_RESET` holds a non-blank value,
     * (re)creates the default admin user (id 0) with the password supplied in `ADMIN_RESET`.
     *
     * Any existing user 0 is deleted first.
     */
    private fun bootstrapAdminReset() {
        val resetPassword = env("ADMIN_RESET")
        if (!isAdminEnabled || resetPassword.isNullOrBlank()) return

        @OptIn(Transactional::class)
        runBlocking {
            try {
                deleteUser(DEFAULT_ADMIN_ID)
            } catch (_: EntityNotFoundException) {}

            createAdmin(
                AdminInsert(
                    UserData(
                        id = DEFAULT_ADMIN_ID,
                        firstName = "Admin",
                        password = resetPassword,
                    )
                )
            )
        }

        logger.warn("ADMIN_RESET applied, default admin user (id=$DEFAULT_ADMIN_ID) recreated. You should redeploy without the environment variable set!")
    }

    companion object {
        private const val PAGE_SIZE = 50
        private const val TOKEN_SIZE = 32
        private const val DEFAULT_ADMIN_ID = 0
        private val secureRand = SecureRandom()
        private val sha256Digest = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }
        private val logger = LoggerFactory.getLogger(UsersService::class.java)

        /**
         * Hashes a high-entropy session token with SHA-256. Session tokens are generated from a
         * cryptographically secure source with sufficient entropy, so a fast hash is adequate and
         * a slow password hash (Argon2) is unnecessary.
         */
        private fun hashSessionToken(token: String): String =
            sha256Digest.get().apply { reset() }
                .digest(token.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }

    /**
     * Gets the [UserType] of the given user ID.
     *
     * @throws EntityNotFoundException if the user does not exist.
     * @throws IllegalStateException if the user is neither a Student, Teacher, nor Admin.
     */
    fun getUserType(id: Int): UserType {
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

    /**
     * Creates a new student with the given information.
     *
     * @throws EntityNotFoundException if any of the specified groups do not exist.
     * @throws IllegalArgumentException if a referenced group does not have the expected type, or if the password does not meet the requirements.
     * @throws ConflictException if a user/student with the same ID already exists.
     */
    suspend fun createStudent(
        id: Int,
        firstName: String,
        gradeId: Int,
        roomId: Int,
        programId: Int? = null,
        prefix: String? = null,
        middleName: String? = null,
        lastName: String? = null,
        password: String,
        avatarUrl: String? = null,
        groupIds: List<Int>? = null,
    ): Student {
        assertGroupHasType(gradeId, GroupType.GRADE)
        assertGroupHasType(roomId, GroupType.ROOM)
        programId?.let { assertGroupHasType(it, GroupType.PROGRAM) }
        assertGroupsHaveType(groupIds.orEmpty(), GroupType.CUSTOM)

        val passwordHash = hashNewPasswordAsync(password)
        val user = createUser(id, firstName, prefix, middleName, lastName, passwordHash, avatarUrl)
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
     * @throws BatchOperationException.MissingGroups if any group does not exist.
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
            throw BatchOperationException.MissingGroups(missing)
        }

        val mismatched = rows.filter { it.value != expected.value }.keys
        require(mismatched.isEmpty()) {
            "Groups $mismatched don't have the expected type: ${expected.name}"
        }
    }

    /**
     * Creates multiple students in a batch operation.
     *
     * @throws BatchOperationException.MissingGroups if any of the specified groups do not exist, with the list of missing group IDs in [BatchOperationException.MissingGroups.ids].
     * @throws BatchOperationException.ConflictingEntities if any user with the same IDs already exists, with the list of conflicting IDs in [BatchOperationException.ConflictingEntities.ids].
     * @throws BatchOperationException.InvalidUserData if any of the user data is invalid with `cause`:
     *   - [IllegalArgumentException] if the password does not meet the requirements, or if any group has the wrong type for its slot.
     */
    suspend fun createStudents(inserts: List<StudentInsert>): List<Student> {
        if (inserts.isEmpty()) return emptyList()

        // Collect every referenced group ID and type so we can validate in one query
        val expectedTypes = mutableMapOf<Int, GroupType>()
        for (insert in inserts) {
            fun add(id: Int, type: GroupType) {
                val prev = expectedTypes[id]
                if (prev != null && prev != type) {
                    throw BatchOperationException.InvalidUserData(
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
        if (missing.isNotEmpty()) throw BatchOperationException.MissingGroups(missing)

        val mismatched = expectedTypes.filter { (id, type) -> existing[id] != type.value }
        if (mismatched.isNotEmpty()) {
            // Attribute the error to the first student that referenced any mismatched group.
            val badId = mismatched.keys.first()
            val owner = inserts.first { insert ->
                badId == insert.gradeId || badId == insert.roomId || badId == insert.programId ||
                        badId in insert.groups
            }

            throw BatchOperationException.InvalidUserData(
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

        return Student.find { Students.id inList prepared.map { it.request.user.id } }
            .with(Student::user, Student::groups)
            .toList()
    }

    private data class PreparedUserInsert<T>(
        val request: T,
        val passwordHash: String
    )

    /**
     * Creates a new teacher with the given information.
     *
     * @throws ConflictException if a user/teacher with the same ID already exists.
     * @throws IllegalArgumentException if the password does not meet the requirements.
     */
    suspend fun createTeacher(
        id: Int,
        firstName: String,
        prefix: String? = null,
        middleName: String? = null,
        lastName: String? = null,
        password: String,
        avatarUrl: String? = null,
        groupIds: List<Int>? = null,
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

        val passwordHash = hashNewPasswordAsync(password)
        val user = createUser(id, firstName, prefix, middleName, lastName, passwordHash, avatarUrl)
        val teacher = Teacher.new(user.id.value) {}

        groupIds?.let { ids ->
            TeacherGroups.batchInsert(ids.distinct()) {
                this[TeacherGroups.teacher] = id
                this[TeacherGroups.group] = it
            }
        }

        return teacher.load(Teacher::user, Teacher::groups)
    }

    /**
     * Creates multiple teachers in a batch operation.
     *
     * @throws BatchOperationException.ConflictingEntities if any user with the same IDs already exists, with the list of conflicting IDs in [BatchOperationException.ConflictingEntities.ids].
     * @throws BatchOperationException.InvalidUserData if any of the user data is invalid with `cause`:
     *   - [IllegalArgumentException] if the password does not meet the requirements.
     * @throws BatchOperationException.MissingGroups if any of the specified groups do not exist.
     */
    suspend fun createTeachers(inserts: List<TeacherInsert>): List<Teacher> {
        if (inserts.isEmpty()) return emptyList()

        // Validate groups
        val allGroupIds = inserts.flatMap { it.groups }.distinct()
        if (allGroupIds.isNotEmpty()) {
            val existing = Groups.select(Groups.id)
                .where { Groups.id inList allGroupIds }
                .map { it[Groups.id].value }
                .toSet()

            val missing = allGroupIds.filter { it !in existing }
            if (missing.isNotEmpty()) throw BatchOperationException.MissingGroups(missing)
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

        return Teacher.find { Teachers.id inList prepared.map { it.request.user.id } }
            .with(Teacher::user, Teacher::groups)
            .toList()
    }

    /**
     * Creates a new admin with the given information.
     *
     * The password follows the same requirements as [createStudent]/[createTeacher].
     *
     * @throws ConflictException if a user/admin with the same ID already exists.
     * @throws IllegalArgumentException if the password does not meet the requirements.
     */
    @Transactional
    suspend fun createAdmin(insert: AdminInsert) = dbQuery {
        val passwordHash = argon2.hash(insert.user.password.assertPasswordRequirements().toCharArray())

        val user = createUser(
            id = insert.user.id,
            firstName = insert.user.firstName,
            prefix = insert.user.prefix,
            middleName = insert.user.middleName,
            lastName = insert.user.lastName,
            passwordHash = passwordHash,
            avatarUrl = insert.user.avatarUrl,
        )

        Admin.new(user.id.value) {}
    }

    private suspend fun <T : UserInsert> insertUserBatch(inserts: List<T>): List<PreparedUserInsert<T>> {
        val conflicts = Users
            .select(Users.id)
            .where { Users.id inList inserts.map { it.user.id } }
            .map { it[Users.id].value }
            .toList()

        if (conflicts.isNotEmpty()) {
            throw BatchOperationException.ConflictingEntities(conflicts)
        }

        val prepared = inserts.map { req ->
            val user = req.user
            val passwordHash = try {
                argon2.hashAsync(user.password.assertPasswordRequirements().toCharArray())
            } catch (e: IllegalArgumentException) {
                throw BatchOperationException.InvalidUserData(user.id, e)
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

    /**
     * Deletes the user with the given ID.
     *
     * @throws EntityNotFoundException if the user does not exist.
     */
    @Transactional
    suspend fun deleteUser(id: Int) {
        val rows = dbQuery { Users.deleteWhere { Users.id eq id } }
        if (rows == 0) {
            throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")
        }

        logger.info("Deleted user: $id")
    }

    /**
     * Deletes the users with the given ID.
     *
     * @throws BatchOperationException.NotFoundEntities if any of the specified IDs do not exist, with the list of missing IDs in [BatchOperationException.NotFoundEntities.ids].
     */
    @Transactional
    suspend fun deleteUsers(id: List<Int>) {
        if (id.isEmpty()) return

        dbQuery {
            val notFoundIds = Users
                .select(Users.id)
                .where { not(Users.id inList id) }
                .map { it[Users.id].value }
                .toList()

            if (notFoundIds.isNotEmpty()) {
                throw BatchOperationException.NotFoundEntities(notFoundIds)
            }

            Users.deleteWhere { Users.id inList id }
        }

        logger.info("Deleted users: ${id.joinToString(", ")}")
    }

    /**
     * Updates the student's profile information.
     *
     * @throws EntityNotFoundException if the user, student, or any referenced group does not exist.
     * @throws IllegalArgumentException if a referenced group does not have the expected type.
     * @throws NothingToUpdateException if there's nothing to update.
     */
    @Transactional
    suspend fun updateStudent(id: Int, update: StudentUpdate) = dbQuery {
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

    /**
     * Updates the teacher's profile information.
     *
     * @throws EntityNotFoundException if the user does not exist.
     * @throws NothingToUpdateException if there's nothing to update.
     */
    @Transactional
    suspend fun updateTeacher(id: Int, update: TeacherUpdate) = dbQuery {
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
                    throw BatchOperationException.MissingGroups(missing)
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

    private fun updateUser(id: Int, update: UserUpdate) {
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

    sealed class BatchOperationException(msg: String, cause: Throwable?) : Exception(msg, cause) {
        constructor(msg: String) : this(msg, null)

        class NotFoundEntities(val ids: List<Int>) : BatchOperationException("Entity with the given IDs does not exist")
        class ConflictingEntities(val ids: List<Int>) :
            BatchOperationException("Entity with the same IDs already exists")

        class MissingGroups(val ids: List<Int>) : BatchOperationException("Groups are missing")
        class InvalidUserData(val id: Int, cause: Throwable) : BatchOperationException("Invalid user data", cause)
    }

    class UserData(
        val id: Int,
        val firstName: String,
        val prefix: String? = null,
        val middleName: String? = null,
        val lastName: String? = null,
        val avatarUrl: String? = null,
        val password: String,
    )

    sealed class UserInsert(val user: UserData)

    /**
     * @param gradeId ID of a GRADE-typed group the student is assigned to (required).
     * @param roomId ID of a ROOM-typed group the student is assigned to (required).
     * @param programId Optional ID of a PROGRAM-typed group the student is assigned to.
     * @param groups Optional list of CUSTOM-typed group IDs to add the student to.
     */
    class StudentInsert(
        user: UserData,
        val gradeId: Int,
        val roomId: Int,
        val programId: Int? = null,
        val groups: List<Int> = emptyList(),
    ) : UserInsert(user)

    class TeacherInsert(user: UserData, val groups: List<Int> = emptyList()) : UserInsert(user)
    class AdminInsert(user: UserData) : UserInsert(user)

    /**
     * If [setPrefix] is true, the prefix is updated to the given value (which may be null).
     * If false, the prefix is left unchanged.
     *
     * If [setMiddleName] is true, the middle name is updated to the given value (which may be null).
     * If false, the middle name is left unchanged.
     *
     * If [setLastName] is true, the last name is updated to the given value (which may be null).
     * If false, the last name is left unchanged.
     *
     * If [setAvatarUrl] is true, the avatar URL is updated to the given value (which may be null).
     * If false, the avatar URL is left unchanged.
     */
    data class UserUpdate(
        val firstName: String?,
        val prefix: String? = null,
        val middleName: String?,
        val lastName: String?,
        val avatarUrl: String?,
        val setPrefix: Boolean = false,
        val setMiddleName: Boolean = false,
        val setLastName: Boolean = false,
        val setAvatarUrl: Boolean = false,
    )

    data class StudentUpdate(
        val user: UserUpdate,
        val groups: List<Int>? = null,
        val gradeId: Int? = null,
        val roomId: Int? = null,
        val programId: Int? = null,
        val setProgramId: Boolean = false,
    )

    data class TeacherUpdate(
        val user: UserUpdate,
        /**
         * If non-null, replaces the teacher's current group memberships with the provided list of group IDs.
         * Groups can be of any [GroupType]. All referenced groups must exist.
         */
        val groups: List<Int>? = null,
    )

    /**
     * Changes the password of the user with the given ID.
     *
     * The password must be at least 4 characters. Leading and trailing spaces are trimmed.
     *
     * @throws EntityNotFoundException if the user does not exist.
     * @throws IllegalArgumentException if the new password does not meet the requirements.
     */
    @Transactional
    suspend fun setPassword(id: Int, newPassword: String) {
        val password = newPassword.assertPasswordRequirements()

        val rows = dbQuery {
            Users.update(where = { Users.id eq id }) {
                it[Users.passwordHash] = argon2.hash(password.toCharArray())
            }
        }

        if (rows == 0) throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")
    }

    fun getTeacherById(id: Int): Teacher? = Teacher.findById(id)?.load(Teacher::user, Teacher::groups)

    fun getStudentById(id: Int): Student? = Student.findById(id)?.load(Student::user, Student::groups)

    fun getAdminById(id: Int): Admin? = Admin.findById(id)

    /**
     * Gets a paginated list of students, optionally filtered by a search query.
     *
     * When [query] is provided, results are filtered by substring match on ID, firstName, middleName, or lastName.
     *
     * @throws IllegalArgumentException if the page number is less than 1.
     *
     * @return A pair of the list of students and the total number of matching students (for pagination purposes).
     */
    suspend fun getStudents(page: Int = 1, query: String? = null): Pair<List<Student>, Long> {
        require(page >= 1) { "Page must be at least 1" }
        val offset = ((page - 1) * PAGE_SIZE).toLong()
        val searchCondition = query?.takeIf { it.isNotBlank() }?.let { userSearchCondition(it) }

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

        return students to count
    }

    /**
     * Gets a paginated list of teachers, optionally filtered by a search query.
     *
     * When [query] is provided, results are filtered by substring match on ID, firstName, middleName, or lastName.
     *
     * @throws IllegalArgumentException if the page number is less than 1.
     *
     * @return A pair of the list of teachers and the total number of matching teachers (for pagination purposes).
     */
    suspend fun getTeachers(page: Int = 1, query: String? = null): Pair<List<Teacher>, Long> {
        require(page >= 1) { "Page must be at least 1" }
        val offset = ((page - 1) * PAGE_SIZE).toLong()
        val searchCondition = query?.takeIf { it.isNotBlank() }?.let { userSearchCondition(it) }

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

        return teachers to count
    }

    /**
     * Validates password and creates a new session for the given user ID.
     *
     * @throws EntityNotFoundException if the user does not exist.
     * @throws IllegalArgumentException if the token or session is invalid.
     */
    @Transactional
    suspend fun createSession(id: Int, password: String, aud: String): String {
        val isAdmin = dbQuery {
            runCatching { getUserType(id) }.getOrNull()
        } == UserType.ADMIN

        val minimumDelay =
            if (isAdmin) config.adminMinimumSessionCreationTime else config.minimumSessionCreationTime
        val sessionDurationSeconds =
            if (isAdmin) config.adminSessionDurationSeconds else config.sessionDurationSeconds

        return withMinimumDelay(minimumDelay) {
            val password = password.assertPasswordRequirements()

            val aud = aud.trim().apply {
                require(!isEmpty()) { "Audience blank for user: $id" }
                require(length <= 256) { "Audience string too long for user: $id (aud = ${slice(0..32)}...)" }
            }

            // Fetch the hash in a short transaction. The connection is returned to the pool before the expensive Argon2 verification
            val passwordHash = dbQuery {
                val user = Users.select(Users.passwordHash).where { Users.id eq id }.singleOrNull()
                    ?: throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")

                user[Users.passwordHash]
            }

            require(passwordHash != null) { "User not password authenticatable: $id" }

            require(argon2.verifyAsync(passwordHash, password.toCharArray())) { "Invalid password for user: $id" }

            // Persist the new session token in a second short transaction
            val token = dbQuery { insecurelyCreateSessionWithoutValidation(id, sessionDurationSeconds) }

            _sessionCreationFlow.emit(id)
            logger.debug("New session created, user: $id, aud: $aud")

            token
        }
    }

    /**
     * Creates a new session for the given user ID without validating the password.
     * Assumes the user exists.
     */
    @Transactional
    fun insecurelyCreateSessionWithoutValidation(id: Int, customDurationSeconds: Long? = null): String {
        val session = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(ByteArray(TOKEN_SIZE).apply { secureRand.nextBytes(this) })

        Users.update({ Users.id eq id }) {
            it[Users.sessionExpiry] =
                LocalDateTime.now().plusSeconds(customDurationSeconds ?: config.sessionDurationSeconds)

            it[Users.sessionHash] = hashSessionToken(session)
        }

        return "$id.$session"
    }

    /**
     * Gets the basic user data associated with the given session token.
     *
     * @throws EntityNotFoundException if the user does not exist.
     * @throws IllegalArgumentException if the token or session is invalid.
     */
    fun getSessionUser(token: String): SessionUser {
        val (subject, session) = token.split(".", limit = 2).takeIf { it.size == 2 }
            ?: throw IllegalArgumentException("Invalid session token format")

        val userId = subject.toIntOrNull() ?: throw IllegalArgumentException("Invalid token subject: $subject")

        val row = Users
            .leftJoin(Students)
            .leftJoin(Teachers)
            .leftJoin(Admins)
            .select(Users.sessionHash, Users.sessionExpiry, Students.id, Teachers.id, Admins.id)
            .where { Users.id eq userId }
            .singleOrNull()
            ?: throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $userId")

        val sessionExpiry = row[Users.sessionExpiry]
            ?: throw IllegalArgumentException("No active session for user: $userId")

        val sessionHash = row[Users.sessionHash]
            ?: throw IllegalArgumentException("No active session for user: $userId")

        if (sessionExpiry.isBefore(LocalDateTime.now()))
            throw IllegalArgumentException("Session expired for user: $userId")

        if (!MessageDigest.isEqual(sessionHash.toByteArray(Charsets.UTF_8), hashSessionToken(session).toByteArray(Charsets.UTF_8)))
            throw IllegalArgumentException("Invalid session token for user: $userId")

        val type = when {
            row.getOrNull(Students.id) != null -> UserType.STUDENT
            row.getOrNull(Teachers.id) != null -> UserType.TEACHER
            row.getOrNull(Admins.id) != null -> UserType.ADMIN
            else -> throw IllegalStateException("User is not a Student, Teacher, or Admin: $userId")
        }

        logger.debug("Validated session token, user: $userId")

        return SessionUser(userId, type)
    }

    class SessionUser(val id: Int, val type: UserType)

    /**
     * Clears the session (logs out) for the user with the given ID.
     */
    fun clearSession(userId: Int) {
        Users.update({ Users.id eq userId }) {
            it[Users.sessionExpiry] = null
            it[Users.sessionHash] = null
        }

        logger.debug("Session cleared, user: $userId")
    }

    /**
     * Validates password requirements and hashes the password on the bounded Argon2 dispatcher,
     * off any held DB connection. Used by the [createStudent]/[createTeacher] paths which run inside
     * the caller's [dbQuery] transaction.
     */
    private suspend fun hashNewPasswordAsync(password: String): String =
        argon2.hashAsync(password.assertPasswordRequirements().toCharArray())

    /**
     * Inserts a [User] row with an already-computed [passwordHash] (null for no-password users).
     * Password validation and hashing are performed by the caller so this stays a pure DB write
     * that can run inside any (blocking or suspended) transaction.
     */
    private fun createUser(
        id: Int,
        firstName: String,
        prefix: String? = null,
        middleName: String?,
        lastName: String?,
        passwordHash: String?,
        avatarUrl: String?,
    ): User {
        val stmt = Users.insertIgnore {
            it[Users.id] = id
            it[Users.prefix] = prefix
            it[Users.firstName] = firstName
            it[Users.middleName] = middleName
            it[Users.lastName] = lastName
            it[Users.passwordHash] = passwordHash
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
