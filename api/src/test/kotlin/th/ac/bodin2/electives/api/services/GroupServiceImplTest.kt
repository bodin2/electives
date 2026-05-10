package th.ac.bodin2.electives.api.services

import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.TestConstants
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import kotlin.test.*

class GroupServiceImplTest : ApplicationTest() {
    private val ApplicationTestBuilder.groupService: GroupService
        get() {
            val service: GroupService by application.dependencies
            return service
        }

    @Test
    fun `get all groups`() = runTest {
        val groups = transaction { groupService.getAll() }
        assertEquals(2, groups.size)
    }

    @Test
    fun `get group by id`() = runTest {
        val group = transaction { groupService.getById(TestConstants.Groups.GROUP_1_ID) }
        assertNotNull(group)
        assertEquals(TestConstants.Groups.GROUP_1_ID, group.id.value)
    }

    @Test
    fun `get group not found`() = runTest {
        val group = transaction { groupService.getById(UNUSED_ID) }
        assertNull(group)
    }

    @Test
    fun `create group`() = runTest {
        val newId = 500

        @OptIn(Transactional::class)
        val created = groupService.create(newId, "New Group")

        assertEquals(newId, created.id.value)

        val fetched = transaction { groupService.getById(newId) }
        assertNotNull(fetched)
        assertEquals("New Group", fetched.name)
    }

    @Test
    fun `create group with duplicate id throws conflict`() = runTest {
        assertFailsWith<ConflictException> {
            @OptIn(Transactional::class)
            groupService.create(TestConstants.Groups.GROUP_1_ID, "Duplicate Group")
        }
    }

    @Test
    fun `delete group`() = runTest {
        // Create a group that's not referenced by any subjects/enrollments
        val tempId = 600
        @OptIn(Transactional::class)
        groupService.create(tempId, "Temporary Group")

        @OptIn(Transactional::class)
        groupService.delete(tempId)

        val fetched = transaction { groupService.getById(tempId) }
        assertNull(fetched)
    }

    @Test
    fun `delete group not found`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            @OptIn(Transactional::class)
            groupService.delete(UNUSED_ID)
        }
    }

    @Test
    fun `update group name`() = runTest {
        @OptIn(Transactional::class)
        groupService.update(
            TestConstants.Groups.GROUP_1_ID,
            GroupService.GroupUpdate(name = "Updated Group")
        )

        val fetched = transaction { groupService.getById(TestConstants.Groups.GROUP_1_ID) }
        assertNotNull(fetched)
        assertEquals("Updated Group", fetched.name)
    }

    @Test
    fun `update group nothing to update`() = runTest {
        assertFailsWith<NothingToUpdateException> {
            @OptIn(Transactional::class)
            groupService.update(
                TestConstants.Groups.GROUP_1_ID,
                GroupService.GroupUpdate()
            )
        }
    }

    @Test
    fun `update group not found`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            @OptIn(Transactional::class)
            groupService.update(
                UNUSED_ID,
                GroupService.GroupUpdate(name = "Does not exist")
            )
        }
    }

    @Test
    fun `get member count`() = runTest {
        val count = transaction { groupService.getMemberCount(TestConstants.Groups.GROUP_1_ID) }
        assertEquals(1, count) // John is in Group 1
    }

    @Test
    fun `get member count empty group`() = runTest {
        val tempId = 500
        @OptIn(Transactional::class)
        groupService.create(tempId, "Empty Group")

        val count = transaction { groupService.getMemberCount(tempId) }
        assertEquals(0, count)
    }

    @Test
    fun `get group member counts`() = runTest {
        val counts = transaction { groupService.getMemberCounts() }
        assertTrue(counts.containsKey(TestConstants.Groups.GROUP_1_ID))
        assertTrue(counts.containsKey(TestConstants.Groups.GROUP_2_ID))
    }

    @Test
    fun `get group members`() = runTest {
        val (members, count) = @OptIn(Transactional::class)
        groupService.getMembers(TestConstants.Groups.GROUP_1_ID)

        assertTrue(count > 0)
        assertEquals(count.toInt(), members.size)
    }

    @Test
    fun `get group members not found`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            @OptIn(Transactional::class)
            groupService.getMembers(UNUSED_ID)
        }
    }

    @Test
    fun `search group members by first name`() = runTest {
        val (members, count) = @OptIn(Transactional::class)
        groupService.getMembers(TestConstants.Groups.GROUP_1_ID, query = TestConstants.Students.JOHN_FIRST_NAME)

        assertTrue(count > 0)
        assertTrue(members.any { it.id.value == TestConstants.Students.JOHN_ID })
    }

    @Test
    fun `search group members with no results`() = runTest {
        val (members, count) = @OptIn(Transactional::class)
        groupService.getMembers(TestConstants.Groups.GROUP_1_ID, query = "nonexistentnameXYZ")

        assertEquals(0L, count)
        assertTrue(members.isEmpty())
    }

    @Test
    fun `search group members by id substring`() = runTest {
        val idSubstring = TestConstants.Students.JOHN_ID.toString().substring(0, 3)
        val (members, count) = @OptIn(Transactional::class)
        groupService.getMembers(TestConstants.Groups.GROUP_1_ID, query = idSubstring)

        assertTrue(count > 0)
        assertTrue(members.any { it.id.value == TestConstants.Students.JOHN_ID })
    }
}
