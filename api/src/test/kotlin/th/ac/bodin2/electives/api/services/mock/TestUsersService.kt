package th.ac.bodin2.electives.api.services.mock

import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.MockUtils.mockStudent
import th.ac.bodin2.electives.api.MockUtils.mockTeacher
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.PASSWORD
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.TEACHER_ID
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.proto.api.UserType

class TestUsersService : UsersService {
    private val hasSessions = mutableSetOf<Int>()

    override fun createStudent(
        id: Int,
        firstName: String,
        middleName: String?,
        lastName: String?,
        password: String,
        avatarUrl: String?,
    ) = error("Not testable")

    override fun createTeacher(
        id: Int,
        firstName: String,
        middleName: String?,
        lastName: String?,
        password: String,
        avatarUrl: String?,
    ) = error("Not testable")

    @CreatesTransaction
    override fun deleteUser(id: Int) = error("Not testable")

    @CreatesTransaction
    override fun updateStudent(id: Int, update: UsersService.StudentUpdate) = error("Not testable")

    @CreatesTransaction
    override fun updateTeacher(id: Int, update: UsersService.TeacherUpdate) = error("Not testable")

    @CreatesTransaction
    override fun setPassword(id: Int, newPassword: String) = error("Not testable")

    override fun getUserType(id: Int) = when (id) {
        TEACHER_ID -> UserType.TEACHER
        STUDENT_ID -> UserType.STUDENT
        else -> throw NotFoundException(ExceptionEntity.USER, "User does not exist: $id")
    }

    @CreatesTransaction
    override suspend fun createSession(id: Int, password: String, aud: String): String {
        if (password != PASSWORD) {
            throw IllegalArgumentException("Invalid password for user ID: $id")
        }

        hasSessions.add(id)
        return id.toString()
    }

    override fun getSessionUserId(token: String): Int {
        if (!hasSessions.contains(token.toInt())) {
            throw IllegalArgumentException("No session for user ID: $token")
        }

        return token.toInt()
    }

    override fun clearSession(userId: Int) {
        hasSessions.remove(userId)
    }

    override fun getTeacherById(id: Int): Teacher? {
        return if (id == TEACHER_ID) {
            mockTeacher(id)
        } else {
            null
        }
    }

    override fun getStudentById(id: Int): Student? {
        return if (id == STUDENT_ID) {
            mockStudent(id)
        } else {
            null
        }
    }

    @CreatesTransaction
    override fun getStudents(page: Int): Pair<List<Student>, Long> {
        return if (page == 1) {
            listOf(mockStudent(STUDENT_ID)) to 1
        } else {
            emptyList<Student>() to 0
        }
    }

    @CreatesTransaction
    override fun getTeachers(page: Int): Pair<List<Teacher>, Long> {
        return if (page == 1) {
            listOf(mockTeacher(TEACHER_ID)) to 1
        } else {
            emptyList<Teacher>() to 0
        }
    }
}