package th.ac.bodin2.electives.api.services

import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.TestConstants
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import kotlin.test.*

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
    @Test
    fun `create elective`() = runTest {
        val newId = 500
        @OptIn(CreatesTransaction::class)
        val created = electiveService.create(newId, "New Elective")

        assertEquals(newId, created.id.value)

        val fetched = transaction { electiveService.getById(newId) }
        assertNotNull(fetched)
        assertEquals("New Elective", fetched.name)
    }

    @Test
    fun `create elective with duplicate id throws conflict`() = runTest {
        assertFailsWith<ConflictException> {
            @OptIn(CreatesTransaction::class)
            electiveService.create(TestConstants.Electives.SCIENCE_ID, "Duplicate Elective")
        }
    }

    @Test
    fun `create elective with team`() = runTest {
        val newId = 501
        @OptIn(CreatesTransaction::class)
        val created = electiveService.create(
            id = newId,
            name = "Team Elective",
            team = TestConstants.Teams.TEAM_1_ID,
        )

        assertEquals(newId, created.id.value)

        val fetched = transaction { electiveService.getById(newId) }
        assertNotNull(fetched)
        assertEquals(TestConstants.Teams.TEAM_1_ID, transaction { fetched.team?.id?.value })
    }

    @Test
    fun `delete elective`() = runTest {
        val tempId = 600
        @OptIn(CreatesTransaction::class)
        electiveService.create(tempId, "Temporary Elective")

        @OptIn(CreatesTransaction::class)
        electiveService.delete(tempId)

        val fetched = transaction { electiveService.getById(tempId) }
        assertNull(fetched)
    }

    @Test
    fun `delete elective not found`() = runTest {
        assertFailsWith<NotFoundException> {
            @OptIn(CreatesTransaction::class)
            electiveService.delete(UNUSED_ID)
        }
    }

    @Test
    fun `update elective name`() = runTest {
        @OptIn(CreatesTransaction::class)
        electiveService.update(
            TestConstants.Electives.SCIENCE_ID,
            ElectiveService.ElectiveUpdate(name = "Updated Science")
        )

        val fetched = transaction { electiveService.getById(TestConstants.Electives.SCIENCE_ID) }
        assertNotNull(fetched)
        assertEquals("Updated Science", fetched.name)
    }

    @Test
    fun `update elective with nothing`() = runTest {
        assertFailsWith<NothingToUpdateException> {
            @OptIn(CreatesTransaction::class)
            electiveService.update(
                TestConstants.Electives.SCIENCE_ID,
                ElectiveService.ElectiveUpdate()
            )
        }
    }

    @Test
    fun `update elective not found`() = runTest {
        assertFailsWith<NotFoundException> {
            @OptIn(CreatesTransaction::class)
            electiveService.update(
                UNUSED_ID,
                ElectiveService.ElectiveUpdate(name = "Does not exist")
            )
        }
    }

    @Test
    fun `set elective subjects`() = runTest {
        @OptIn(CreatesTransaction::class)
        electiveService.setSubjects(
            TestConstants.Electives.SCIENCE_ID,
            listOf(TestConstants.Subjects.PHYSICS_ID)
        )

        val subjects = transaction { electiveService.getSubjects(TestConstants.Electives.SCIENCE_ID) }.getOrThrow()
        assertEquals(1, subjects.size)
        assertEquals(TestConstants.Subjects.PHYSICS_ID, subjects[0].id.value)
    }

    @Test
    fun `set elective subjects empty`() = runTest {
        @OptIn(CreatesTransaction::class)
        electiveService.setSubjects(TestConstants.Electives.SCIENCE_ID, emptyList())

        val subjects = transaction { electiveService.getSubjects(TestConstants.Electives.SCIENCE_ID) }.getOrThrow()
        assertEquals(0, subjects.size)
    }

    @Test
    fun `set elective subjects not found`() = runTest {
        assertFailsWith<NotFoundException> {
            @OptIn(CreatesTransaction::class)
            electiveService.setSubjects(UNUSED_ID, listOf(TestConstants.Subjects.PHYSICS_ID))
        }
    }

    @Test
    fun `create elective with non-existent team throws not found`() = runTest {
        assertFailsWith<NotFoundException> {
            @OptIn(CreatesTransaction::class)
            electiveService.create(
                id = 700,
                name = "Invalid Team Elective",
                team = UNUSED_ID
            )
        }
    }

    @Test
    fun `update elective with non-existent team throws not found`() = runTest {
        assertFailsWith<NotFoundException> {
            @OptIn(CreatesTransaction::class)
            electiveService.update(
                TestConstants.Electives.SCIENCE_ID,
                ElectiveService.ElectiveUpdate(setTeam = true, team = UNUSED_ID)
            )
        }
    }

    @Test
    fun `set subjects with non-existent subject throws not found`() = runTest {
        assertFailsWith<NotFoundException> {
            @OptIn(CreatesTransaction::class)
            electiveService.setSubjects(
                TestConstants.Electives.SCIENCE_ID,
                listOf(UNUSED_ID)
            )
        }
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