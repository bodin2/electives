package th.ac.bodin2.electives.api.services.mock

import kotlinx.coroutines.flow.SharedFlow
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.MockUtils.mockAdmin
import th.ac.bodin2.electives.api.MockUtils.mockStudent
import th.ac.bodin2.electives.api.MockUtils.mockTeacher
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ADMIN_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ADMIN_TOKEN
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.PASSWORD
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.TEACHER_ID
import th.ac.bodin2.electives.db.Admin
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.proto.api.UserType

class TestUsersService : UsersService {
    private val hasSessions = mutableSetOf<Int>()

    override val sessionCreationFlow: SharedFlow<Int>
        get() = error("Not testable")

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
    ) = error("Not testable")

    @Transactional
    override fun createStudents(inserts: List<UsersService.StudentInsert>) = error("Not testable")

    override fun createTeacher(
        id: Int,
        firstName: String,
        prefix: String?,
        middleName: String?,
        lastName: String?,
        password: String,
        avatarUrl: String?,
    ) = error("Not testable")

    @Transactional
    override fun createTeachers(inserts: List<UsersService.TeacherInsert>) = error("Not testable")

    @Transactional
    override fun createAdmin(insert: UsersService.AdminInsert) = error("Not testable")

    @Transactional
    override fun deleteUser(id: Int) = error("Not testable")

    @Transactional
    override suspend fun deleteUsers(id: List<Int>) = error("Not testable")

    @Transactional
    override fun updateStudent(id: Int, update: UsersService.StudentUpdate): Student {
        if (id != STUDENT_ID) throw EntityNotFoundException(ExceptionEntity.STUDENT)
        return mockStudent(id)
    }

    @Transactional
    override fun updateTeacher(id: Int, update: UsersService.TeacherUpdate): Teacher {
        if (id != TEACHER_ID) throw EntityNotFoundException(ExceptionEntity.TEACHER)
        return mockTeacher(id)
    }

    @Transactional
    override fun setPassword(id: Int, newPassword: String) {
        getUserType(id) // throws if user not found
    }

    override fun getUserType(id: Int) = when (id) {
        ADMIN_ID -> UserType.ADMIN
        TEACHER_ID -> UserType.TEACHER
        STUDENT_ID -> UserType.STUDENT
        else -> throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")
    }

    @Transactional
    override suspend fun createSession(id: Int, password: String, aud: String): String {
        if (password != PASSWORD) {
            throw IllegalArgumentException("Invalid password for user ID: $id")
        }

        return insecurelyCreateSessionWithoutValidation(id)
    }

    @Transactional
    override fun insecurelyCreateSessionWithoutValidation(id: Int, customDurationSeconds: Long?): String {
        hasSessions.add(id)
        return id.toString()
    }

    override fun getSessionUser(token: String): UsersService.SessionUser {
        if (token == ADMIN_TOKEN) {
            return UsersService.SessionUser(
                id = ADMIN_ID,
                type = UserType.ADMIN
            )
        }

        val id = token.toIntOrNull()
            ?: throw IllegalArgumentException("No session for token: $token")

        if (!hasSessions.contains(id)) {
            throw IllegalArgumentException("No session for user ID: $id")
        }

        return UsersService.SessionUser(
            id = id,
            type = getUserType(id)
        )
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

    override fun getAdminById(id: Int): Admin? {
        return if (id == ADMIN_ID) {
            mockAdmin(id)
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

    @Transactional
    override fun getStudents(page: Int, query: String?): Pair<List<Student>, Long> {
        return if (page == 1) {
            listOf(mockStudent(STUDENT_ID)) to 1
        } else {
            emptyList<Student>() to 0
        }
    }

    @Transactional
    override fun getTeachers(page: Int, query: String?): Pair<List<Teacher>, Long> {
        return if (page == 1) {
            listOf(mockTeacher(TEACHER_ID)) to 1
        } else {
            emptyList<Teacher>() to 0
        }
    }
}