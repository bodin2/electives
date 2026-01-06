package th.ac.bodin2.electives.api

import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.api.services.ElectiveService
import th.ac.bodin2.electives.api.services.TestServiceConstants.UNUSED_ID
import kotlin.test.Test
import kotlin.test.assertEquals

class ElectiveServiceImplTest : ApplicationTest() {
    private val ApplicationTestBuilder.electiveService: ElectiveService
        get() {
            val service: ElectiveService by application.dependencies
            return service
        }

    @Test
    fun `get elective`() = runTest {
        val elective = transaction { electiveService.getById(TestConstants.Electives.SCIENCE_ID) }
        assertEquals(TestConstants.Electives.SCIENCE_ID, elective?.id?.value)
    }

    @Test
    fun `get elective not found`() = runTest {
        val elective = transaction { electiveService.getById(UNUSED_ID) }
        assertEquals(null, elective)
    }


    @Test
    fun `get elective subjects`() = runTest {
        val subjects = transaction { electiveService.getSubjects(TestConstants.Electives.SCIENCE_ID) }.getOrThrow()
        assert(subjects.size == 2)

        val physics = subjects.find { it.id.value == TestConstants.Subjects.PHYSICS_ID }
        val chemistry = subjects.find { it.id.value == TestConstants.Subjects.CHEMISTRY_ID }

        assert(physics != null)
        assert(chemistry != null)
    }

    @Test
    fun `get elective not found subjects`() = runTest {
        transaction { electiveService.getSubjects(UNUSED_ID) }.assertElectiveNotFound()
    }

    @Test
    fun `get elective subject`() = runTest {
        val subject = transaction {
            electiveService.getSubject(
                TestConstants.Electives.SCIENCE_ID,
                TestConstants.Subjects.PHYSICS_ID
            )
        }.getOrThrow()

        assertEquals(TestConstants.Subjects.PHYSICS_ID, subject.id.value)
    }

    @Test
    fun `get elective not found subject`() = runTest {
        transaction {
            electiveService.getSubject(
                UNUSED_ID,
                TestConstants.Subjects.PHYSICS_ID
            )
        }.assertElectiveNotFound()
    }

    @Test
    fun `get elective subject not in elective`() = runTest {
        transaction {
            electiveService.getSubject(
                TestConstants.Electives.SCIENCE_ID,
                TestConstants.Subjects.OTHER_ID
            )
        }.assertSubjectNotFound()
    }

    fun <T> ElectiveService.QueryResult<T>.getOrThrow(): T = when (this) {
        is ElectiveService.QueryResult.Success -> this.value
        is ElectiveService.QueryResult.ElectiveNotFound -> throw NoSuchElementException("Elective not found")
        is ElectiveService.QueryResult.SubjectNotFound -> throw NoSuchElementException("Subject not found")
        is ElectiveService.QueryResult.SubjectNotPartOfElective -> throw IllegalArgumentException("Subject not part of elective")
    }

    @Test
    fun `get subject not found`() = runTest {
        transaction {
            electiveService.getSubject(
                TestConstants.Electives.SCIENCE_ID,
                UNUSED_ID
            )
        }.assertSubjectNotFound()
    }

    fun <T> ElectiveService.QueryResult<T>.assertElectiveNotFound() {
        when (this) {
            is ElectiveService.QueryResult.ElectiveNotFound -> {
                // Expected
            }

            else -> {
                assert(false) { "Expected ElectiveNotFound but got $this" }
            }
        }
    }

    fun <T> ElectiveService.QueryResult<T>.assertSubjectNotFound() {
        when (this) {
            is ElectiveService.QueryResult.SubjectNotFound -> {
                // Expected
            }

            else -> {
                assert(false) { "Expected SubjectNotFound but got $this" }
            }
        }
    }
}