package th.ac.bodin2.electives.api.services.mock

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.proto.api.UserType

object TestUsersService {
    @OptIn(Transactional::class)
    operator fun invoke(): UsersService {
        val hasSessions = mutableSetOf<Int>()

        fun userType(id: Int) = when (id) {
            ADMIN_ID -> UserType.ADMIN
            TEACHER_ID -> UserType.TEACHER
            STUDENT_ID -> UserType.STUDENT
            else -> throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")
        }

        return mockk(relaxed = true) {
            every { getUserType(any()) } answers { userType(firstArg()) }

            coEvery { updateStudent(any(), any()) } answers {
                val id = firstArg<Int>()
                if (id != STUDENT_ID) throw EntityNotFoundException(ExceptionEntity.STUDENT)
                mockStudent(id)
            }

            coEvery { updateTeacher(any(), any()) } answers {
                val id = firstArg<Int>()
                if (id != TEACHER_ID) throw EntityNotFoundException(ExceptionEntity.TEACHER)
                mockTeacher(id)
            }

            coEvery { setPassword(any(), any()) } answers {
                userType(firstArg()) // throws if user not found
            }

            coEvery { createSession(any(), any(), any()) } answers {
                val id = firstArg<Int>()
                val password = secondArg<String>()
                if (password != PASSWORD) {
                    throw IllegalArgumentException("Invalid password for user ID: $id")
                }
                hasSessions.add(id)
                id.toString()
            }

            every { insecurelyCreateSessionWithoutValidation(any(), any()) } answers {
                val id = firstArg<Int>()
                hasSessions.add(id)
                id.toString()
            }

            every { getSessionUser(any()) } answers {
                val token = firstArg<String>()
                if (token == ADMIN_TOKEN) {
                    return@answers UsersService.SessionUser(id = ADMIN_ID, type = UserType.ADMIN)
                }

                val id = token.toIntOrNull()
                    ?: throw IllegalArgumentException("No session for token: $token")

                if (!hasSessions.contains(id)) {
                    throw IllegalArgumentException("No session for user ID: $id")
                }

                UsersService.SessionUser(id = id, type = userType(id))
            }

            every { clearSession(any()) } answers {
                hasSessions.remove(firstArg())
                Unit
            }

            every { getTeacherById(any()) } answers {
                val id = firstArg<Int>()
                if (id == TEACHER_ID) mockTeacher(id) else null
            }

            every { getAdminById(any()) } answers {
                val id = firstArg<Int>()
                if (id == ADMIN_ID) mockAdmin(id) else null
            }

            every { getStudentById(any()) } answers {
                val id = firstArg<Int>()
                if (id == STUDENT_ID) mockStudent(id) else null
            }

            coEvery { getStudents(any(), any()) } answers {
                val page = firstArg<Int>()
                if (page == 1) listOf(mockStudent(STUDENT_ID)) to 1L
                else emptyList<Student>() to 0L
            }

            coEvery { getTeachers(any(), any()) } answers {
                val page = firstArg<Int>()
                if (page == 1) listOf(mockTeacher(TEACHER_ID)) to 1L
                else emptyList<Teacher>() to 0L
            }
        }
    }
}
