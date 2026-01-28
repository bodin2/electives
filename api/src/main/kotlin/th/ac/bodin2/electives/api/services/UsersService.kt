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

    fun getTeacherById(id: Int): Teacher?
    fun getStudentById(id: Int): Student?

    /**
     * Gets the user ID associated with the given session token.
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