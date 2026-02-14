package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.proto.api.UserType

interface UsersService {
    /**
     * Gets the [UserType] of the given user ID.
     *
     * @throws NotFoundException if the user does not exist.
     * @throws IllegalStateException if the user is neither a Student nor a Teacher.
     */
    fun getUserType(id: Int): UserType
    fun createStudent(
        id: Int,
        firstName: String,
        middleName: String? = null,
        lastName: String? = null,
        password: String,
        avatarUrl: String? = null,
    ): Student

    fun createTeacher(
        id: Int,
        firstName: String,
        middleName: String? = null,
        lastName: String? = null,
        password: String,
        avatarUrl: String? = null,
    ): Teacher

    /**
     * Deletes the teacher or student with the given ID.
     *
     * @throws NotFoundException if the user does not exist.
     */
    @CreatesTransaction
    fun deleteUser(id: Int)

    /**
     * Updates the student's profile information.
     *
     * @throws NotFoundException if the user or team does not exist.
     */
    @CreatesTransaction
    fun updateStudent(
        id: Int,
        update: StudentUpdate,
    )

    /**
     * Updates the teacher's profile information.
     *
     * @throws NotFoundException if the user does not exist.
     */
    @CreatesTransaction
    fun updateTeacher(
        id: Int,
        update: TeacherUpdate,
    )

    /**
     * If [setMiddleName] is true, the middle name is updated to the given value (which may be null).
     * If false, the middle name is left unchanged.
     *
     * If [setAvatarUrl] is true, the avatar URL is updated to the given value (which may be null).
     * If false, the avatar URL is left unchanged.
     */
    data class UserUpdate(
        val firstName: String?,
        val middleName: String?,
        val lastName: String?,
        val avatarUrl: String?,
        val setMiddleName: Boolean = false,
        val setAvatarUrl: Boolean = false,
    )

    data class StudentUpdate(
        val update: UserUpdate,
        val teams: List<Int>? = null,
    )

    data class TeacherUpdate(
        val update: UserUpdate,
    )

    /**
     * Changes the password of the user with the given ID.
     *
     * The password must be at least 4 characters. Leading and trailing spaces are trimmed.
     *
     * @throws NotFoundException if the user does not exist.
     * @throws IllegalArgumentException if the new password does not meet the requirements.
     */
    @CreatesTransaction
    fun setPassword(id: Int, newPassword: String)

    fun getTeacherById(id: Int): Teacher?
    fun getStudentById(id: Int): Student?

    /**
     * Gets a paginated list of students.
     *
     * @throws IllegalArgumentException if the page number is less than 1.
     *
     * @return A pair of the list of students and the total number of students (for pagination purposes).
     */
    @CreatesTransaction
    fun getStudents(page: Int = 1): Pair<List<Student>, Long>
    /**
     * Gets a paginated list of teachers.
     *
     * @throws IllegalArgumentException if the page number is less than 1.
     *
     * @return A pair of the list of teachers and the total number of teachers (for pagination purposes).
     */
    @CreatesTransaction
    fun getTeachers(page: Int = 1): Pair<List<Teacher>, Long>

    /**
     * Validates password and creates a new session for the given user ID.
     *
     * @throws NotFoundException if the user does not exist.
     * @throws IllegalArgumentException if the token or session is invalid.
     */
    @CreatesTransaction
    suspend fun createSession(id: Int, password: String, aud: String): String

    /**
     * Gets the user ID associated with the given session token.
     *
     * @throws NotFoundException if the user does not exist.
     * @throws IllegalArgumentException if the token or session is invalid.
     */
    fun getSessionUserId(token: String): Int

    /**
     * Clears the session (logs out) for the user with the given ID.
     */
    fun clearSession(userId: Int)
}