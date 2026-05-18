package th.ac.bodin2.electives.api.services

import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.TestConstants
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.db.models.StudentClasses
import th.ac.bodin2.electives.db.models.TeacherSubjects
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.batchInsert
import kotlin.test.*

class EnrollmentServiceImplTest : ApplicationTest() {
    private val ApplicationTestBuilder.enrollmentService: EnrollmentService
        get() {
            val service: EnrollmentService by application.dependencies
            return service
        }

    @Test
    fun `get enrollment`() = runTest {
        val enrollment = transaction { enrollmentService.getById(TestConstants.Enrollments.SCIENCE_ID) }
        assertEquals(TestConstants.Enrollments.SCIENCE_ID, enrollment?.id?.value)
    }

    @Test
    fun `get enrollment not found`() = runTest {
        val enrollment = transaction { enrollmentService.getById(UNUSED_ID) }
        assertEquals(null, enrollment)
    }


    @Test
    fun `get enrollment subjects`() = runTest {
        val subjects = transaction { enrollmentService.getSubjects(TestConstants.Enrollments.SCIENCE_ID) }.getOrThrow()
        assert(subjects.size == 2)

        val physics = subjects.find { it.id.value == TestConstants.Subjects.PHYSICS_ID }
        val chemistry = subjects.find { it.id.value == TestConstants.Subjects.CHEMISTRY_ID }

        assert(physics != null)
        assert(chemistry != null)
    }

    @Test
    fun `get enrollment not found subjects`() = runTest {
        transaction { enrollmentService.getSubjects(UNUSED_ID) }.assertEnrollmentNotFound()
    }

    @Test
    fun `get enrollment subject`() = runTest {
        val subject = transaction {
            enrollmentService.getSubject(
                TestConstants.Enrollments.SCIENCE_ID,
                TestConstants.Subjects.PHYSICS_ID
            )
        }.getOrThrow()

        assertEquals(TestConstants.Subjects.PHYSICS_ID, subject.id.value)
    }

    @Test
    fun `get enrollment not found subject`() = runTest {
        transaction {
            enrollmentService.getSubject(
                UNUSED_ID,
                TestConstants.Subjects.PHYSICS_ID
            )
        }.assertEnrollmentNotFound()
    }

    @Test
    fun `get enrollment subject not in enrollment`() = runTest {
        transaction {
            enrollmentService.getSubject(
                TestConstants.Enrollments.SCIENCE_ID,
                TestConstants.Subjects.OTHER_ID
            )
        }.assertSubjectNotFound()
    }

    fun <T> EnrollmentService.QueryResult<T>.getOrThrow(): T = when (this) {
        is EnrollmentService.QueryResult.Success -> this.value
        is EnrollmentService.QueryResult.EnrollmentNotFound -> throw NoSuchElementException("Enrollment not found")
        is EnrollmentService.QueryResult.SubjectNotFound -> throw NoSuchElementException("Subject not found")
        is EnrollmentService.QueryResult.SubjectNotPartOfEnrollment -> throw IllegalArgumentException("Subject not part of enrollment")
    }

    @Test
    fun `get subject not found`() = runTest {
        transaction {
            enrollmentService.getSubject(
                TestConstants.Enrollments.SCIENCE_ID,
                UNUSED_ID
            )
        }.assertSubjectNotFound()
    }

    @Test
    fun `create enrollment`() = runTest {
        val newId = 500

        @OptIn(Transactional::class)
        val created = enrollmentService.create(newId, "New Enrollment")

        assertEquals(newId, created.id.value)

        val fetched = transaction { enrollmentService.getById(newId) }
        assertNotNull(fetched)
        assertEquals("New Enrollment", fetched.name)
    }

    @Test
    fun `create enrollment with duplicate id throws conflict`() = runTest {
        assertFailsWith<ConflictException> {
            @OptIn(Transactional::class)
            enrollmentService.create(TestConstants.Enrollments.SCIENCE_ID, "Duplicate Enrollment")
        }
    }

    @Test
    fun `create enrollment with group`() = runTest {
        val newId = 501

        @OptIn(Transactional::class)
        val created = enrollmentService.create(
            id = newId,
            name = "Group Enrollment",
            group = TestConstants.Groups.GROUP_1_ID,
        )

        assertEquals(newId, created.id.value)

        val fetched = transaction { enrollmentService.getById(newId) }
        assertNotNull(fetched)
        assertEquals(TestConstants.Groups.GROUP_1_ID, transaction { fetched.groupId?.value })
    }

    @Test
    fun `delete enrollment`() = runTest {
        val tempId = 600
        @OptIn(Transactional::class)
        enrollmentService.create(tempId, "Temporary Enrollment")

        @OptIn(Transactional::class)
        enrollmentService.delete(tempId)

        val fetched = transaction { enrollmentService.getById(tempId) }
        assertNull(fetched)
    }

    @Test
    fun `delete enrollment not found`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            @OptIn(Transactional::class)
            enrollmentService.delete(UNUSED_ID)
        }
    }

    @Test
    fun `update enrollment name`() = runTest {
        @OptIn(Transactional::class)
        enrollmentService.update(
            TestConstants.Enrollments.SCIENCE_ID,
            EnrollmentService.EnrollmentUpdate(name = "Updated Science")
        )

        val fetched = transaction { enrollmentService.getById(TestConstants.Enrollments.SCIENCE_ID) }
        assertNotNull(fetched)
        assertEquals("Updated Science", fetched.name)
    }

    @Test
    fun `update enrollment with nothing`() = runTest {
        assertFailsWith<NothingToUpdateException> {
            @OptIn(Transactional::class)
            enrollmentService.update(
                TestConstants.Enrollments.SCIENCE_ID,
                EnrollmentService.EnrollmentUpdate()
            )
        }
    }

    @Test
    fun `update enrollment not found`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            @OptIn(Transactional::class)
            enrollmentService.update(
                UNUSED_ID,
                EnrollmentService.EnrollmentUpdate(name = "Does not exist")
            )
        }
    }

    @Test
    fun `set enrollment subjects`() = runTest {
        @OptIn(Transactional::class)
        enrollmentService.setSubjects(
            TestConstants.Enrollments.SCIENCE_ID,
            listOf(TestConstants.Subjects.PHYSICS_ID)
        )

        val subjects = transaction { enrollmentService.getSubjects(TestConstants.Enrollments.SCIENCE_ID) }.getOrThrow()
        assertEquals(1, subjects.size)
        assertEquals(TestConstants.Subjects.PHYSICS_ID, subjects[0].id.value)
    }

    @Test
    fun `set enrollment subjects empty`() = runTest {
        @OptIn(Transactional::class)
        enrollmentService.setSubjects(TestConstants.Enrollments.SCIENCE_ID, emptyList())

        val subjects = transaction { enrollmentService.getSubjects(TestConstants.Enrollments.SCIENCE_ID) }.getOrThrow()
        assertEquals(0, subjects.size)
    }

    @Test
    fun `set subjects unenrolls removed subjects`() = runTest {
        val enrollmentId = 900
        val subject1 = TestConstants.Subjects.PHYSICS_ID
        val subject2 = TestConstants.Subjects.CHEMISTRY_ID

        @OptIn(Transactional::class)
        enrollmentService.create(enrollmentId, "Test Enrollment")

        // 1. Ensure both subjects are in the enrollment
        @OptIn(Transactional::class)
        enrollmentService.setSubjects(enrollmentId, listOf(subject1, subject2))

        transaction {
            // 2. Enroll a student in subject 1 and another in subject 2
            StudentClasses.insert {
                it[student] = TestConstants.Students.JOHN_ID
                it[enrollment] = enrollmentId
                it[subject] = subject1
            }
            StudentClasses.insert {
                it[student] = TestConstants.Students.JANE_ID
                it[enrollment] = enrollmentId
                it[subject] = subject2
            }

            // 3. Assign a teacher to subject 1 and another to subject 2
            TeacherSubjects.insert {
                it[teacher] = TestConstants.Teachers.BOB_ID
                it[enrollment] = enrollmentId
                it[subject] = subject1
            }
            TeacherSubjects.insert {
                it[teacher] = TestConstants.Teachers.ALICE_ID
                it[enrollment] = enrollmentId
                it[subject] = subject2
            }
        }

        // 4. Update enrollment to only include subject 2 (subject 1 is removed)
        @OptIn(Transactional::class)
        enrollmentService.setSubjects(enrollmentId, listOf(subject2))

        transaction {
            // 5. Verify subject 1 records are gone, but subject 2 records remain
            val studentClasses = StudentClasses.selectAll().where { StudentClasses.enrollment eq enrollmentId }.toList()
            assertEquals(1, studentClasses.size)
            assertEquals(TestConstants.Students.JANE_ID, studentClasses[0][StudentClasses.student].value)
            assertEquals(subject2, studentClasses[0][StudentClasses.subject].value)

            val teacherSubjects = TeacherSubjects.selectAll().where { TeacherSubjects.enrollment eq enrollmentId }.toList()
            assertEquals(1, teacherSubjects.size)
            assertEquals(TestConstants.Teachers.ALICE_ID, teacherSubjects[0][TeacherSubjects.teacher].value)
            assertEquals(subject2, teacherSubjects[0][TeacherSubjects.subject].value)
        }
    }

    @Test
    fun `set enrollment subjects not found`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            @OptIn(Transactional::class)
            enrollmentService.setSubjects(UNUSED_ID, listOf(TestConstants.Subjects.PHYSICS_ID))
        }
    }

    @Test
    fun `get enrolled count with no enrollments`() = runTest {
        val count = transaction { enrollmentService.getEnrolledCount(TestConstants.Enrollments.SCIENCE_ID) }
        assertEquals(0, count)
    }

    @Test
    fun `get enrolled count with enrollments`() = runTest {
        transaction {
            StudentClasses.insert {
                it[student] = TestConstants.Students.JOHN_ID
                it[enrollment] = TestConstants.Enrollments.SCIENCE_ID
                it[subject] = TestConstants.Subjects.PHYSICS_ID
            }
        }
        val count = transaction { enrollmentService.getEnrolledCount(TestConstants.Enrollments.SCIENCE_ID) }
        assertEquals(1, count)
    }

    @Test
    fun `get enrolled count for non-existent enrollment returns zero`() = runTest {
        val count = transaction { enrollmentService.getEnrolledCount(UNUSED_ID) }
        assertEquals(0, count)
    }

    @Test
    fun `create enrollment with non-existent group throws not found`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            @OptIn(Transactional::class)
            enrollmentService.create(
                id = 700,
                name = "Invalid Group Enrollment",
                group = UNUSED_ID
            )
        }
    }

    @Test
    fun `update enrollment with non-existent group throws not found`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            @OptIn(Transactional::class)
            enrollmentService.update(
                TestConstants.Enrollments.SCIENCE_ID,
                EnrollmentService.EnrollmentUpdate(setGroup = true, group = UNUSED_ID)
            )
        }
    }

    @Test
    fun `set subjects with non-existent subject throws not found`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            @OptIn(Transactional::class)
            enrollmentService.setSubjects(
                TestConstants.Enrollments.SCIENCE_ID,
                listOf(UNUSED_ID)
            )
        }
    }

    fun <T> EnrollmentService.QueryResult<T>.assertEnrollmentNotFound() {
        when (this) {
            is EnrollmentService.QueryResult.EnrollmentNotFound -> {
                // Expected
            }

            else -> {
                assert(false) { "Expected EnrollmentNotFound but got $this" }
            }
        }
    }

    fun <T> EnrollmentService.QueryResult<T>.assertSubjectNotFound() {
        when (this) {
            is EnrollmentService.QueryResult.SubjectNotFound -> {
                // Expected
            }

            else -> {
                assert(false) { "Expected SubjectNotFound but got $this" }
            }
        }
    }
}
