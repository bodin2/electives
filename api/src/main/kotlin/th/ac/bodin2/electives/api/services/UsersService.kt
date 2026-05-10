package th.ac.bodin2.electives.api.services

import kotlinx.coroutines.flow.SharedFlow
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.db.Admin
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.proto.api.UserType
import java.security.PublicKey

interface UsersService {
    val sessionCreationFlow: SharedFlow<Int>

    /**
     * Gets the [UserType] of the given user ID.
     *
     * @throws EntityNotFoundException if the user does not exist.
     * @throws IllegalStateException if the user is neither a Student, Teacher, nor Admin.
     */
    fun getUserType(id: Int): UserType

    /**
     * Creates a new student with the given information.
     *
     * @throws EntityNotFoundException if any of the specified groups do not exist.
     * @throws ConflictException if a user/student with the same ID already exists.
     * @throws IllegalArgumentException if the password does not meet the requirements.
     */
    fun createStudent(
        id: Int,
        firstName: String,
        prefix: String? = null,
        middleName: String? = null,
        lastName: String? = null,
        password: String,
        avatarUrl: String? = null,
        groups: List<Int>? = null,
    ): Student

    /**
     * Creates multiple students in a batch operation.
     *
     * @throws BatchOperationException.MissingGroups if any of the specified groups do not exist, with the list of missing group IDs in [UsersService.BatchOperationException.MissingGroups.ids].
     * @throws BatchOperationException.ConflictingEntities if any user with the same IDs already exists, with the list of conflicting IDs in [UsersService.BatchOperationException.ConflictingEntities.ids].
     * @throws BatchOperationException.InvalidUserData if any of the user data is invalid with `cause`:
     *   - [IllegalArgumentException] if the password does not meet the requirements.
     */
    @Transactional
    fun createStudents(inserts: List<StudentInsert>): List<Student>

    /**
     * Creates a new teacher with the given information.
     *
     * @throws ConflictException if a user/teacher with the same ID already exists.
     * @throws IllegalArgumentException if the password does not meet the requirements.
     */
    fun createTeacher(
        id: Int,
        firstName: String,
        prefix: String? = null,
        middleName: String? = null,
        lastName: String? = null,
        password: String,
        avatarUrl: String? = null,
    ): Teacher

    /**
     * Creates multiple teachers in a batch operation.
     *
     * @throws BatchOperationException.ConflictingEntities if any user with the same IDs already exists, with the list of conflicting IDs in [BatchOperationException.ConflictingEntities.ids].
     * @throws BatchOperationException.InvalidUserData if any of the user data is invalid with `cause`:
     *   - [IllegalArgumentException] if the password does not meet the requirements.
     */
    @Transactional
    fun createTeachers(inserts: List<TeacherInsert>): List<Teacher>

    /**
     * Creates a new admin with the given information.
     *
     * @throws ConflictException if a user/admin with the same ID already exists.
     * @throws IllegalArgumentException if the pass
     */
    @Transactional
    fun createAdmin(insert: AdminInsert): Admin

    /**
     * Deletes the user with the given ID.
     *
     * @throws EntityNotFoundException if the user does not exist.
     */
    @Transactional
    fun deleteUser(id: Int)

    /**
     * Deletes the users with the given ID.
     *
     * @throws BatchOperationException.NotFoundEntities if any of the specified IDs do not exist, with the list of missing IDs in [BatchOperationException.NotFoundEntities.ids].
     */
    @Transactional
    suspend fun deleteUsers(id: List<Int>)

    /**
     * Updates the student's profile information.
     *
     * @throws EntityNotFoundException if the user or team does not exist.
     * @throws NothingToUpdateException if there's nothing to update.
     */
    @Transactional
    fun updateStudent(
        id: Int,
        update: StudentUpdate,
    ): Student

    /**
     * Updates the teacher's profile information.
     *
     * @throws EntityNotFoundException if the user does not exist.
     * @throws NothingToUpdateException if there's nothing to update.
     */
    @Transactional
    fun updateTeacher(
        id: Int,
        update: TeacherUpdate,
    ): Teacher

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
    class StudentInsert(user: UserData, val groups: List<Int> = emptyList()) : UserInsert(user)
    class TeacherInsert(user: UserData) : UserInsert(user)
    class AdminInsert(user: UserData, val publicKey: PublicKey) : UserInsert(user)

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
    )

    data class TeacherUpdate(
        val user: UserUpdate,
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
    fun setPassword(id: Int, newPassword: String)

    fun getTeacherById(id: Int): Teacher?
    fun getStudentById(id: Int): Student?
    fun getAdminById(id: Int): Admin?

    /**
     * Gets a paginated list of students, optionally filtered by a search query.
     *
     * When [query] is provided, results are filtered by substring match on ID, firstName, middleName, or lastName.
     *
     * @throws IllegalArgumentException if the page number is less than 1.
     *
     * @return A pair of the list of students and the total number of matching students (for pagination purposes).
     */
    @Transactional
    fun getStudents(page: Int = 1, query: String? = null): Pair<List<Student>, Long>

    /**
     * Gets a paginated list of teachers, optionally filtered by a search query.
     *
     * When [query] is provided, results are filtered by substring match on ID, firstName, middleName, or lastName.
     *
     * @throws IllegalArgumentException if the page number is less than 1.
     *
     * @return A pair of the list of teachers and the total number of matching teachers (for pagination purposes).
     */
    @Transactional
    fun getTeachers(page: Int = 1, query: String? = null): Pair<List<Teacher>, Long>

    /**
     * Validates password and creates a new session for the given user ID.
     *
     * @throws EntityNotFoundException if the user does not exist.
     * @throws IllegalArgumentException if the token or session is invalid.
     */
    @Transactional
    suspend fun createSession(id: Int, password: String, aud: String): String

    /**
     * Creates a new session for the given user ID without validating the password.
     * Assumes the user exists.
     */
    @Transactional
    fun insecurelyCreateSessionWithoutValidation(id: Int, customDurationSeconds: Long? = null): String

    /**
     * Gets the basic user data associated with the given session token.
     *
     * @throws EntityNotFoundException if the user does not exist.
     * @throws IllegalArgumentException if the token or session is invalid.
     */
    fun getSessionUser(token: String): SessionUser

    class SessionUser(val id: Int, val type: UserType)

    /**
     * Clears the session (logs out) for the user with the given ID.
     */
    fun clearSession(userId: Int)
}