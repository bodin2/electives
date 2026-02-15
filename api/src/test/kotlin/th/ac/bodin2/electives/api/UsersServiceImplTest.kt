package th.ac.bodin2.electives.api

import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.TestConstants.Students
import th.ac.bodin2.electives.api.TestConstants.Teachers
import th.ac.bodin2.electives.api.TestConstants.TestData
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.api.services.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.services.UsersServiceImpl
import th.ac.bodin2.electives.proto.api.UserType
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

@OptIn(CreatesTransaction::class)
class UsersServiceImplTest : ApplicationTest() {
    private val ApplicationTestBuilder.usersService: UsersService
        get() {
            val usersService: UsersService by application.dependencies
            return usersService
        }

    @Test
    fun `get student user type`() = runTest {
        val userType = transaction { usersService.getUserType(Students.JOHN_ID) }
        assertEquals(UserType.STUDENT, userType)

    }

    @Test
    fun `get teacher user type`() = runTest {
        val userType = transaction { usersService.getUserType(Teachers.BOB_ID) }
        assertEquals(UserType.TEACHER, userType)

    }

    @Test
    fun `get non existent user type`() = runTest {
        assertFailsWith<NotFoundException> {
            transaction { usersService.getUserType(UNUSED_ID) }
        }
    }

    @Test
    fun `create student`() = runTest {
        transaction {
            val student = usersService.createStudent(1010, "New", "Student", "User", "testpass")
            assertNotNull(student)
            assertEquals(1010, student.id)
            assertEquals("New", student.user.firstName)
        }
    }

    @Test
    fun `create teacher`() = runTest {
        transaction {
            val teacher = usersService.createTeacher(
                2010,
                "New",
                null,
                "Teacher",
                "testpass",
                ""
            )

            assertNotNull(teacher)
            assertEquals(2010, teacher.id)
            assertEquals("New", teacher.user.firstName)
        }
    }

    @Test
    fun `create student with duplicate id throws conflict`() = runTest {
        assertFailsWith<ConflictException> {
            transaction {
                usersService.createStudent(
                    Students.JOHN_ID,
                    "Duplicate",
                    null,
                    "Student",
                    "testpass",
                    null
                )
            }
        }
    }

    @Test
    fun `create teacher with duplicate id throws conflict`() = runTest {
        assertFailsWith<ConflictException> {
            transaction {
                usersService.createTeacher(
                    Teachers.BOB_ID,
                    "Duplicate",
                    null,
                    "Teacher",
                    "testpass",
                    null
                )
            }
        }
    }

    @Test
    fun `create user session`() = runTest {
        val token = usersService.createSession(Students.JOHN_ID, Students.JOHN_PASSWORD, TestData.CLIENT_NAME)

        assertNotNull(token)
        assertTrue(token.isNotBlank())

        val userId = transaction {
            usersService.getSessionUserId(token)
        }
        assertEquals(Students.JOHN_ID, userId)
    }

    @Test
    fun `create user session with invalid password`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            usersService.createSession(Students.JOHN_ID, "wrongpassword", TestData.CLIENT_NAME)
        }
    }

    @Test
    fun `create non existent user session`() = runTest {
        assertFailsWith<NotFoundException> {
            usersService.createSession(UNUSED_ID, Students.JOHN_PASSWORD, TestData.CLIENT_NAME)
        }
    }

    @Test
    fun `get session with invalid token`() = runTest {
        assertFailsWith<Exception> {
            transaction {
                usersService.getSessionUserId("")
            }
        }
    }

    @Test
    fun `clear session`() = runTest {
        val token = usersService.createSession(Students.JOHN_ID, Students.JOHN_PASSWORD, TestData.CLIENT_NAME)

        transaction {
            usersService.clearSession(Students.JOHN_ID)
        }

        assertFailsWith<IllegalArgumentException> {
            transaction {
                usersService.getSessionUserId(token)
            }
        }
    }

    @Test
    fun `get student by ID`() = runTest {
        val student = transaction {
            usersService.getStudentById(Students.JOHN_ID)
        }

        assertNotNull(student)
        assertEquals(Students.JOHN_ID, student.id)
    }

    @Test
    fun `get non existent student by ID`() = runTest {
        val student = transaction {
            usersService.getStudentById(UNUSED_ID)
        }

        assertNull(student)
    }

    @Test
    fun `get teacher by ID`() = runTest {
        val teacher = transaction {
            usersService.getTeacherById(Teachers.BOB_ID)
        }

        assertNotNull(teacher)
        assertEquals(Teachers.BOB_ID, teacher.id)
    }

    @Test
    fun `get non existent teacher by ID`() = runTest {
        val teacher = transaction {
            usersService.getTeacherById(UNUSED_ID)
        }

        assertNull(teacher)
    }

    @Test
    fun `create session with empty password`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            usersService.createSession(
                Students.JOHN_ID,
                "",
                TestData.CLIENT_NAME
            )
        }
    }

    @Test
    fun `create session with too long password`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            usersService.createSession(
                Students.JOHN_ID,
                "p".repeat(TestConstants.Limits.PASSWORD_TOO_LONG),
                TestData.CLIENT_NAME
            )
        }
    }

    @Test
    fun `create session with too long client name`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            usersService.createSession(
                Students.JOHN_ID,
                Students.JOHN_PASSWORD,
                "c".repeat(TestConstants.Limits.CLIENT_NAME_TOO_LONG)
            )
        }
    }

    @Test
    fun `create session with empty client name`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            usersService.createSession(Students.JOHN_ID, Students.JOHN_PASSWORD, "")
        }
    }

    @Test
    fun `session invalidates after multiple sessions created`() = runTest {
        val token1 = usersService.createSession(Students.JOHN_ID, Students.JOHN_PASSWORD, "client1")
        val token2 = usersService.createSession(Students.JOHN_ID, Students.JOHN_PASSWORD, "client2")

        assertNotEquals(token1, token2)

        assertFailsWith<IllegalArgumentException> {
            transaction {
                usersService.getSessionUserId(token1)
            }
        }

        val userId = transaction {
            usersService.getSessionUserId(token2)
        }
        assertEquals(Students.JOHN_ID, userId)
    }

    @Test
    fun `generate unique session tokens`() = runTest {
        val token1 = usersService.createSession(Students.JOHN_ID, Students.JOHN_PASSWORD, "client1")
        val token2 = usersService.createSession(Students.JOHN_ID, Students.JOHN_PASSWORD, "client1")

        assertNotEquals(token1, token2)
    }

    @Test
    fun `session expires`() = runTest {
        val usersService = UsersServiceImpl(
            UsersServiceImpl.Config(
                sessionDurationSeconds = 1,
                minimumSessionCreationTime = 0.seconds
            )
        )

        val token = usersService.createSession(Students.JOHN_ID, Students.JOHN_PASSWORD, TestData.CLIENT_NAME)

        // Wait for session to expire
        delay(1001L)

        assertFailsWith<IllegalArgumentException> {
            transaction { usersService.getSessionUserId(token) }
        }
    }

    @Test
    fun `session creation takes at least set minimum`() = runTest {
        val usersService = UsersServiceImpl(
            UsersServiceImpl.Config(
                sessionDurationSeconds = 5,
                minimumSessionCreationTime = 1.seconds
            )
        )

        val time = measureTime {
            usersService.createSession(Students.JOHN_ID, Students.JOHN_PASSWORD, TestData.CLIENT_NAME)
        }

        assert(time >= 1.seconds) { "Session creation took less than minimum time" }
    }

    @Test
    fun `delete user`() = runTest {
        usersService.deleteUser(Students.JOHN_ID)

        val student = transaction { usersService.getStudentById(Students.JOHN_ID) }
        assertNull(student)
    }

    @Test
    fun `delete non existent user`() = runTest {
        assertFailsWith<NotFoundException> {
            usersService.deleteUser(UNUSED_ID)
        }
    }

    @Test
    fun `update student`() = runTest {
        usersService.updateStudent(
            Students.JOHN_ID,
            UsersService.StudentUpdate(
                update = UsersService.UserUpdate(
                    firstName = "Updated",
                    middleName = null,
                    lastName = null,
                    avatarUrl = null,
                ),
                teams = listOf(TestConstants.Teams.TEAM_1_ID)
            )
        )

        val student = transaction { usersService.getStudentById(Students.JOHN_ID) }
        assertNotNull(student)
        assertEquals("Updated", student.user.firstName)
    }

    @Test
    fun `update student nonexistent team`() = runTest {
        assertFailsWith<NotFoundException> {
            usersService.updateStudent(
                Students.JOHN_ID,
                UsersService.StudentUpdate(
                    update = UsersService.UserUpdate(
                        firstName = null,
                        middleName = null,
                        lastName = null,
                        avatarUrl = null,
                    ),
                    teams = listOf(UNUSED_ID)
                )
            )
        }
    }

    @Test
    fun `update non existent student`() = runTest {
        assertFailsWith<NotFoundException> {
            usersService.updateStudent(
                UNUSED_ID,
                UsersService.StudentUpdate(
                    update = UsersService.UserUpdate(
                        firstName = "Nope",
                        middleName = null,
                        lastName = null,
                        avatarUrl = null,
                    )
                )
            )
        }
    }

    @Test
    fun `update teacher name`() = runTest {
        usersService.updateTeacher(
            Teachers.BOB_ID,
            UsersService.TeacherUpdate(
                update = UsersService.UserUpdate(
                    firstName = "Robert",
                    middleName = null,
                    lastName = null,
                    avatarUrl = null,
                )
            )
        )

        val teacher = transaction { usersService.getTeacherById(Teachers.BOB_ID) }
        assertNotNull(teacher)
        assertEquals("Robert", teacher.user.firstName)
    }

    @Test
    fun `update non existent teacher`() = runTest {
        assertFailsWith<NotFoundException> {
            usersService.updateTeacher(
                UNUSED_ID,
                UsersService.TeacherUpdate(
                    update = UsersService.UserUpdate(
                        firstName = "Nope",
                        middleName = null,
                        lastName = null,
                        avatarUrl = null,
                    )
                )
            )
        }
    }

    @Test
    fun `set password`() = runTest {
        usersService.setPassword(Students.JOHN_ID, "newpassword123")

        // Old password should fail
        assertFailsWith<IllegalArgumentException> {
            usersService.createSession(Students.JOHN_ID, Students.JOHN_PASSWORD, TestData.CLIENT_NAME)
        }

        // New password should work
        val token = usersService.createSession(Students.JOHN_ID, "newpassword123", TestData.CLIENT_NAME)
        assertNotNull(token)
    }

    @Test
    fun `set password too short`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            usersService.setPassword(Students.JOHN_ID, "abc")
        }
    }

    @Test
    fun `set password non existent user`() = runTest {
        assertFailsWith<NotFoundException> {
            usersService.setPassword(UNUSED_ID, "newpassword123")
        }
    }

    @Test
    fun `list students`() = runTest {
        val (students, total) = usersService.getStudents()

        assertTrue(students.isNotEmpty())
        // In tests, we should have students < PAGE_SIZE
        assertEquals(total, students.size.toLong())
        assertTrue(students.any { it.id == Students.JOHN_ID })
    }

    @Test
    fun `list teachers`() = runTest {
        val (teachers, total) = usersService.getTeachers()

        assertTrue(teachers.isNotEmpty())
        // In tests, we should have students < PAGE_SIZE
        assertEquals(total, teachers.size.toLong())
        assertTrue(teachers.any { it.id == Teachers.BOB_ID })
    }

    @Test
    fun `list students on empty page`() = runTest {
        val (students, total) = usersService.getStudents(page = 100)

        assertTrue(students.isEmpty())
        assertTrue(total >= 0)
    }

    @Test
    fun `list students on invalid page fails`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            usersService.getStudents(page = 0)
        }
    }

    @Test
    fun `list teachers on invalid page fails`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            usersService.getTeachers(page = 0)
        }
    }

    @Test
    fun `create student with short password fails`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            transaction {
                usersService.createStudent(
                    id = UNUSED_ID,
                    firstName = "Test",
                    middleName = null,
                    lastName = "User",
                    password = "abc",
                    avatarUrl = null
                )
            }
        }
    }

    @Test
    fun `create student with long password fails`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            transaction {
                usersService.createStudent(
                    id = UNUSED_ID,
                    firstName = "Test",
                    middleName = null,
                    lastName = "User",
                    password = "a".repeat(4097),
                    avatarUrl = null
                )
            }
        }
    }

    @Test
    fun `create teacher with short password fails`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            transaction {
                usersService.createTeacher(
                    id = UNUSED_ID,
                    firstName = "Test",
                    middleName = null,
                    lastName = "Teacher",
                    password = "abc",
                    avatarUrl = null
                )
            }
        }
    }

    @Test
    fun `create teacher with long password fails`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            transaction {
                usersService.createTeacher(
                    id = UNUSED_ID,
                    firstName = "Test",
                    middleName = null,
                    lastName = "Teacher",
                    password = "a".repeat(4097),
                    avatarUrl = null
                )
            }
        }
    }

    @Test
    fun `create session with long password fails`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            usersService.createSession(
                Students.JOHN_ID,
                "a".repeat(4097),
                TestData.CLIENT_NAME
            )
        }
    }

    @Test
    fun `set password with long password fails`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            usersService.setPassword(Students.JOHN_ID, "a".repeat(4097))
        }
    }

    @Test
    fun `password trimming works for create session`() = runTest {
        usersService.setPassword(Students.JOHN_ID, "  testpass  ")

        val token = usersService.createSession(Students.JOHN_ID, "  testpass  ", TestData.CLIENT_NAME)
        assertNotNull(token)
    }

    @Test
    fun `password trimming works for set password`() = runTest {
        usersService.setPassword(Students.JOHN_ID, "   newpass   ")

        val token = usersService.createSession(Students.JOHN_ID, "   newpass   ", TestData.CLIENT_NAME)
        assertNotNull(token)
    }

    @Test
    fun `password with exactly 4 chars works`() = runTest {
        usersService.setPassword(Students.JOHN_ID, "1234")
        val token = usersService.createSession(Students.JOHN_ID, "1234", TestData.CLIENT_NAME)
        assertNotNull(token)
    }

    @Test
    fun `password with exactly 4096 chars works`() = runTest {
        val longPassword = "a".repeat(4096)
        usersService.setPassword(Students.JOHN_ID, longPassword)
        val token = usersService.createSession(Students.JOHN_ID, longPassword, TestData.CLIENT_NAME)
        assertNotNull(token)
    }
}


