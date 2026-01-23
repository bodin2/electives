package th.ac.bodin2.electives.api

import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
                ByteArray(1024) { it.toByte() })

            assertNotNull(teacher)
            assertEquals(2010, teacher.id)
            assertEquals("New", teacher.user.firstName)
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
}
