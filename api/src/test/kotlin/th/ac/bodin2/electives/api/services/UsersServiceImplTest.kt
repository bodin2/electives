package th.ac.bodin2.electives.api.services

import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.TestConstants
import th.ac.bodin2.electives.api.TestConstants.Students
import th.ac.bodin2.electives.api.TestConstants.Teachers
import th.ac.bodin2.electives.api.TestConstants.TestData
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.utils.Argon2
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

@OptIn(Transactional::class)
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
        assertFailsWith<EntityNotFoundException> {
            transaction { usersService.getUserType(UNUSED_ID) }
        }
    }

    @Test
    fun `create student`() = runTest {
        transaction {
            val student = usersService.createStudent(
                1010u,
                "New",
                "Student",
                "User",
                "testpass",
                teams = listOf(TestConstants.Teams.TEAM_1_ID)
            )

            assertNotNull(student)
            assertEquals(1010u, student.id.value)
            assertEquals("New", student.user.firstName)
            assertEquals(TestConstants.Teams.TEAM_1_ID, student.teams.first().id.value)
        }
    }

    @Test
    fun `create teacher`() = runTest {
        transaction {
            val teacher = usersService.createTeacher(
                2010u,
                "New",
                null,
                "Teacher",
                "testpass",
                ""
            )

            assertNotNull(teacher)
            assertEquals(2010u, teacher.id.value)
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
    fun `create student session`() = runTest {
        val token = usersService.createSession(Students.JOHN_ID, Students.JOHN_PASSWORD, TestData.CLIENT_NAME)

        assertNotNull(token)
        assertTrue(token.isNotBlank())

        val user = transaction {
            usersService.getSessionUser(token)
        }

        assertEquals(Students.JOHN_ID, user.id)
        assertEquals(UserType.STUDENT, user.type)
    }

    @Test
    fun `create teacher session`() = runTest {
        val token = usersService.createSession(Teachers.BOB_ID, Teachers.BOB_PASSWORD, TestData.CLIENT_NAME)

        assertNotNull(token)
        assertTrue(token.isNotBlank())

        val user = transaction {
            usersService.getSessionUser(token)
        }

        assertEquals(Teachers.BOB_ID, user.id)
        assertEquals(UserType.TEACHER, user.type)
    }

    @Test
    fun `create user session with invalid password`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            usersService.createSession(Students.JOHN_ID, "wrongpassword", TestData.CLIENT_NAME)
        }
    }

    @Test
    fun `create non existent user session`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            usersService.createSession(UNUSED_ID, Students.JOHN_PASSWORD, TestData.CLIENT_NAME)
        }
    }

    @Test
    fun `get session with invalid token`() = runTest {
        assertFailsWith<Exception> {
            transaction {
                usersService.getSessionUser("")
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
                usersService.getSessionUser(token)
            }
        }
    }

    @Test
    fun `get student by ID`() = runTest {
        val student = transaction {
            usersService.getStudentById(Students.JOHN_ID)
        }

        assertNotNull(student)
        assertEquals(Students.JOHN_ID, student.id.value)
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
        assertEquals(Teachers.BOB_ID, teacher.id.value)
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
                usersService.getSessionUser(token1)
            }
        }

        val user = transaction {
            usersService.getSessionUser(token2)
        }
        assertEquals(Students.JOHN_ID, user.id)
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
            ),
            application.dependencies.resolve<Argon2>()
        )

        val token = usersService.createSession(Students.JOHN_ID, Students.JOHN_PASSWORD, TestData.CLIENT_NAME)

        // Wait for session to expire
        delay(1001.milliseconds)

        assertFailsWith<IllegalArgumentException> {
            transaction { usersService.getSessionUser(token) }
        }
    }

    @Test
    fun `session creation takes at least set minimum`() = runTest {
        val usersService = UsersServiceImpl(
            UsersServiceImpl.Config(
                sessionDurationSeconds = 5,
                minimumSessionCreationTime = 1.seconds
            ),
            application.dependencies.resolve<Argon2>()
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
        assertFailsWith<EntityNotFoundException> {
            usersService.deleteUser(UNUSED_ID)
        }
    }

    @Test
    fun `update student`() = runTest {
        usersService.updateStudent(
            Students.JOHN_ID,
            UsersService.StudentUpdate(
                user = UsersService.UserUpdate(
                    firstName = "Updated",
                    middleName = null,
                    lastName = null,
                    avatarUrl = null,
                ),
                teams = listOf(TestConstants.Teams.TEAM_1_ID)
            )
        )

        transaction {
            val student = usersService.getStudentById(Students.JOHN_ID)
            assertNotNull(student)
            assertEquals("Updated", student.user.firstName)
        }
    }

    @Test
    fun `update student nonexistent team`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            usersService.updateStudent(
                Students.JOHN_ID,
                UsersService.StudentUpdate(
                    user = UsersService.UserUpdate(
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
        assertFailsWith<EntityNotFoundException> {
            usersService.updateStudent(
                UNUSED_ID,
                UsersService.StudentUpdate(
                    user = UsersService.UserUpdate(
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
                user = UsersService.UserUpdate(
                    firstName = "Robert",
                    middleName = null,
                    lastName = null,
                    avatarUrl = null,
                )
            )
        )

        transaction {
            val teacher = usersService.getTeacherById(Teachers.BOB_ID)
            assertNotNull(teacher)
            assertEquals("Robert", teacher.user.firstName)
        }
    }

    @Test
    fun `update non existent teacher`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            usersService.updateTeacher(
                UNUSED_ID,
                UsersService.TeacherUpdate(
                    user = UsersService.UserUpdate(
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
        assertFailsWith<EntityNotFoundException> {
            usersService.setPassword(UNUSED_ID, "newpassword123")
        }
    }

    @Test
    fun `list students`() = runTest {
        val (students, total) = usersService.getStudents()

        assertTrue(students.isNotEmpty())
        // In tests, we should have students < PAGE_SIZE
        assertEquals(total, students.size.toLong())
        assertTrue(students.any { it.id.value == Students.JOHN_ID })
    }

    @Test
    fun `list teachers`() = runTest {
        val (teachers, total) = usersService.getTeachers()

        assertTrue(teachers.isNotEmpty())
        // In tests, we should have students < PAGE_SIZE
        assertEquals(total, teachers.size.toLong())
        assertTrue(teachers.any { it.id.value == Teachers.BOB_ID })
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
    fun `search students by first name`() = runTest {
        val (students, total) = usersService.getStudents(query = Students.JOHN_FIRST_NAME)

        assertTrue(students.isNotEmpty())
        assertTrue(transaction {
            students.all {
                it.user.firstName.contains(
                    Students.JOHN_FIRST_NAME,
                    ignoreCase = true
                )
            }
        })
        assertEquals(total, students.size.toLong())
    }

    @Test
    fun `search students by last name`() = runTest {
        val (students, total) = usersService.getStudents(query = Students.JOHN_LAST_NAME)

        assertTrue(students.isNotEmpty())
        assertTrue(students.any { it.id.value == Students.JOHN_ID })
        assertEquals(total, students.size.toLong())
    }

    @Test
    fun `search students by id substring`() = runTest {
        val idSubstring = Students.JOHN_ID.toString().substring(0, 3)
        val (students, total) = usersService.getStudents(query = idSubstring)

        assertTrue(students.isNotEmpty())
        assertTrue(students.any { it.id.value == Students.JOHN_ID })
        assertEquals(total, students.size.toLong())
    }

    @Test
    fun `search students with no results`() = runTest {
        val (students, total) = usersService.getStudents(query = "nonexistentnameXYZ")

        assertTrue(students.isEmpty())
        assertEquals(0L, total)
    }

    @Test
    fun `search students with blank query returns all`() = runTest {
        val (allStudents, allTotal) = usersService.getStudents()
        val (blankStudents, blankTotal) = usersService.getStudents(query = "  ")

        assertEquals(allTotal, blankTotal)
        assertEquals(allStudents.size, blankStudents.size)
    }

    @Test
    fun `search teachers by first name`() = runTest {
        val (teachers, total) = usersService.getTeachers(query = Teachers.BOB_FIRST_NAME)

        assertTrue(teachers.isNotEmpty())
        assertTrue(teachers.any { it.id.value == Teachers.BOB_ID })
        assertEquals(total, teachers.size.toLong())
    }

    @Test
    fun `search teachers with no results`() = runTest {
        val (teachers, total) = usersService.getTeachers(query = "nonexistentnameXYZ")

        assertTrue(teachers.isEmpty())
        assertEquals(0L, total)
    }

    @Test
    fun `search students by middle name`() = runTest {
        val (students, _) = usersService.getStudents(query = Students.JOHN_MIDDLE_NAME)

        assertTrue(students.isNotEmpty())
        assertTrue(students.any { it.id.value == Students.JOHN_ID })
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

    @Test
    fun `create students batch`() = runTest {
        val inserts = listOf(
            UsersService.StudentInsert(
                UsersService.UserData(3001u, "Alice", null, "Smith", password = "testpass"),
                teams = listOf(TestConstants.Teams.TEAM_1_ID)
            ),
            UsersService.StudentInsert(
                UsersService.UserData(3002u, "Bob", null, "Jones", password = "testpass"),
                teams = emptyList()
            ),
        )

        val students = transaction { usersService.createStudents(inserts) }

        assertEquals(2, students.size)
        assertTrue(students.any { it.id.value == 3001u })
        assertTrue(students.any { it.id.value == 3002u })

        transaction {
            val alice = usersService.getStudentById(3001u)
            assertNotNull(alice)
            assertEquals(TestConstants.Teams.TEAM_1_ID, alice.teams.first().id.value)
        }
    }

    @Test
    fun `create students batch empty list`() = runTest {
        val students = transaction { usersService.createStudents(emptyList()) }
        assertTrue(students.isEmpty())
    }

    @Test
    fun `create students batch with missing team throws`() = runTest {
        val inserts = listOf(
            UsersService.StudentInsert(
                UsersService.UserData(3003u, "Charlie", password = "testpass"),
                teams = listOf(UNUSED_ID)
            )
        )

        val ex = assertFailsWith<UsersService.BatchOperationException.MissingTeams> {
            transaction { usersService.createStudents(inserts) }
        }
        assertTrue(UNUSED_ID in ex.ids)
    }

    @Test
    fun `create students batch with duplicate id throws`() = runTest {
        val inserts = listOf(
            UsersService.StudentInsert(
                UsersService.UserData(Students.JOHN_ID, "Duplicate", password = "testpass")
            )
        )

        val ex = assertFailsWith<UsersService.BatchOperationException.ConflictingEntities> {
            transaction { usersService.createStudents(inserts) }
        }
        assertTrue(Students.JOHN_ID in ex.ids)
    }

    @Test
    fun `create students batch multiple duplicates reports all ids`() = runTest {
        val inserts = listOf(
            UsersService.StudentInsert(
                UsersService.UserData(Students.JOHN_ID, "Dup1", password = "testpass")
            ),
            UsersService.StudentInsert(
                UsersService.UserData(Students.JANE_ID, "Dup2", password = "testpass")
            ),
        )

        val ex = assertFailsWith<UsersService.BatchOperationException.ConflictingEntities> {
            transaction { usersService.createStudents(inserts) }
        }
        assertTrue(Students.JOHN_ID in ex.ids)
        assertTrue(Students.JANE_ID in ex.ids)
    }

    @Test
    fun `create students batch with short password throws`() = runTest {
        val inserts = listOf(
            UsersService.StudentInsert(
                UsersService.UserData(3004u, "Short", password = "abc")
            )
        )

        val ex = assertFailsWith<UsersService.BatchOperationException.InvalidUserData> {
            transaction { usersService.createStudents(inserts) }
        }
        assertEquals(3004u, ex.id)
        assertIs<IllegalArgumentException>(ex.cause)
    }

    @Test
    fun `create students batch with long password throws`() = runTest {
        val inserts = listOf(
            UsersService.StudentInsert(
                UsersService.UserData(3005u, "Long", password = "a".repeat(4097))
            )
        )

        val ex = assertFailsWith<UsersService.BatchOperationException.InvalidUserData> {
            transaction { usersService.createStudents(inserts) }
        }
        assertEquals(3005u, ex.id)
        assertIs<IllegalArgumentException>(ex.cause)
    }

    @Test
    fun `create teachers batch`() = runTest {
        val inserts = listOf(
            UsersService.TeacherInsert(
                UsersService.UserData(4001u, "Carol", null, "White", password = "testpass")
            ),
            UsersService.TeacherInsert(
                UsersService.UserData(4002u, "Dave", null, "Black", password = "testpass")
            ),
        )

        val teachers = transaction { usersService.createTeachers(inserts) }

        assertEquals(2, teachers.size)
        assertTrue(teachers.any { it.id.value == 4001u })
        assertTrue(teachers.any { it.id.value == 4002u })
    }

    @Test
    fun `create teachers batch empty list`() = runTest {
        val teachers = transaction { usersService.createTeachers(emptyList()) }
        assertTrue(teachers.isEmpty())
    }

    @Test
    fun `create teachers batch with duplicate id throws`() = runTest {
        val inserts = listOf(
            UsersService.TeacherInsert(
                UsersService.UserData(Teachers.BOB_ID, "Duplicate", password = "testpass")
            )
        )

        val ex = assertFailsWith<UsersService.BatchOperationException.ConflictingEntities> {
            transaction { usersService.createTeachers(inserts) }
        }
        assertTrue(Teachers.BOB_ID in ex.ids)
    }

    @Test
    fun `create teachers batch with short password throws`() = runTest {
        val inserts = listOf(
            UsersService.TeacherInsert(
                UsersService.UserData(4003u, "Short", password = "abc")
            )
        )

        val ex = assertFailsWith<UsersService.BatchOperationException.InvalidUserData> {
            transaction { usersService.createTeachers(inserts) }
        }
        assertEquals(4003u, ex.id)
        assertIs<IllegalArgumentException>(ex.cause)
    }

    @Test
    fun `create teachers batch with long password throws`() = runTest {
        val inserts = listOf(
            UsersService.TeacherInsert(
                UsersService.UserData(4004u, "Long", password = "a".repeat(4097))
            )
        )

        val ex = assertFailsWith<UsersService.BatchOperationException.InvalidUserData> {
            transaction { usersService.createTeachers(inserts) }
        }
        assertEquals(4004u, ex.id)
        assertIs<IllegalArgumentException>(ex.cause)
    }

    @Test
    fun `session creation flow triggers on session create`() = runTest {
        coroutineScope {
            val collectJob = launch {
                usersService.sessionCreationFlow.first()
            }

            yield() // let the collector start before we emit

            suspendTransaction {
                usersService.createSession(
                    Students.JOHN_ID,
                    Students.JOHN_PASSWORD,
                    TestData.CLIENT_NAME
                )
            }

            collectJob.join()
        }
    }

    @Test
    fun `insecurely creating sessions with custom duration works`() = runTest {
        val token = transaction {
            usersService.insecurelyCreateSessionWithoutValidation(
                Students.JOHN_ID,
                customDurationSeconds = 1
            )
        }

        assertNotNull(token)
        assertTrue(token.isNotBlank())

        // Wait for session to expire
        delay(1001.milliseconds)

        assertFailsWith<IllegalArgumentException> {
            transaction { usersService.getSessionUser(token) }
        }
    }
}


