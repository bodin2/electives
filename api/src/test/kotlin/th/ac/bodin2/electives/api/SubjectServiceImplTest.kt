package th.ac.bodin2.electives.api

import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.services.SubjectService
import th.ac.bodin2.electives.api.services.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.proto.api.SubjectTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class SubjectServiceImplTest : ApplicationTest() {
    private val ApplicationTestBuilder.subjectService: SubjectService
        get() {
            val service: SubjectService by application.dependencies
            return service
        }

    @Test
    fun `get all subjects`() = runTest {
        val subjects = transaction { subjectService.getAll() }
        assertEquals(2, subjects.size)
    }

    @Test
    fun `get subject by id`() = runTest {
        val subject = transaction { subjectService.getById(TestConstants.Subjects.PHYSICS_ID) }
        assertNotNull(subject)
        assertEquals(TestConstants.Subjects.PHYSICS_ID, subject.id.value)
    }

    @Test
    fun `get subject not found`() = runTest {
        val subject = transaction { subjectService.getById(UNUSED_ID) }
        assertNull(subject)
    }

    @Test
    fun `create subject`() = runTest {
        val newId = 500
        @OptIn(CreatesTransaction::class)
        val created = subjectService.create(
            id = newId,
            name = "Biology",
            description = "Introduction to Biology",
            code = "BIO101",
            tag = SubjectTag.SCIENCE_AND_TECHNOLOGY,
            location = "Room B201",
            capacity = 20,
            team = TestConstants.Teams.TEAM_1_ID,
            teacherIds = listOf(TestConstants.Teachers.BOB_ID),
        )

        assertEquals(newId, created.id.value)

        val fetched = transaction { subjectService.getById(newId) }
        assertNotNull(fetched)
        assertEquals("Biology", fetched.name)
    }

    @Test
    fun `delete subject`() = runTest {
        @OptIn(CreatesTransaction::class)
        subjectService.delete(TestConstants.Subjects.PHYSICS_ID)

        val fetched = transaction { subjectService.getById(TestConstants.Subjects.PHYSICS_ID) }
        assertNull(fetched)
    }

    @Test
    fun `delete subject not found`() = runTest {
        assertFailsWith<NotFoundException> {
            @OptIn(CreatesTransaction::class)
            subjectService.delete(UNUSED_ID)
        }
    }

    @Test
    fun `update subject name`() = runTest {
        @OptIn(CreatesTransaction::class)
        subjectService.update(
            TestConstants.Subjects.PHYSICS_ID,
            SubjectService.SubjectUpdate(name = "Advanced Physics")
        )

        val fetched = transaction { subjectService.getById(TestConstants.Subjects.PHYSICS_ID) }
        assertNotNull(fetched)
        assertEquals("Advanced Physics", fetched.name)
    }

    @Test
    fun `update subject not found`() = runTest {
        assertFailsWith<NotFoundException> {
            @OptIn(CreatesTransaction::class)
            subjectService.update(
                UNUSED_ID,
                SubjectService.SubjectUpdate(name = "Does not exist")
            )
        }
    }
}
