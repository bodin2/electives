package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.services.MockUtils.mockStudent
import th.ac.bodin2.electives.api.services.MockUtils.mockTeacher
import th.ac.bodin2.electives.api.services.TestServiceConstants.PASSWORD
import th.ac.bodin2.electives.api.services.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.TEACHER_ID
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
        password: String
    ) = error("Not testable")

    override fun createTeacher(
        id: Int,
        firstName: String,
        middleName: String?,
        lastName: String?,
        password: String,
        avatar: ByteArray?
    ) = error("Not testable")

    override fun getUserType(id: Int) = when (id) {
        TEACHER_ID -> UserType.TEACHER
        STUDENT_ID -> UserType.STUDENT
        else -> throw NotFoundException(NotFoundEntity.USER, "User does not exist: $id")
    }

    override fun createSession(id: Int, password: String, aud: String): String {
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

}