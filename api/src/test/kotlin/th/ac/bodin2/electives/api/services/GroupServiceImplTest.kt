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
import th.ac.bodin2.electives.proto.api.GroupType
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
        // 2 custom groups + 3 slotted groups (GRADE, ROOM, PROGRAM)
        assertEquals(5, groups.size)
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
    fun `delete CUSTOM group with members succeeds`() = runTest {
        // GROUP_2 is CUSTOM and has Jane as a member; CUSTOM groups are freely deletable
        // (StudentGroups rows cascade). GROUP_2 is not referenced by any Subject/Enrollment.
        @OptIn(Transactional::class)
        groupService.delete(TestConstants.Groups.GROUP_2_ID)

        val fetched = transaction { groupService.getById(TestConstants.Groups.GROUP_2_ID) }
        assertNull(fetched)
    }

    @Test
    fun `delete PROGRAM group with members succeeds`() = runTest {
        // PROGRAM memberships are server-side optional and may be cleared on delete.
        @OptIn(Transactional::class)
        groupService.delete(TestConstants.Groups.PROGRAM_ID)

        val fetched = transaction { groupService.getById(TestConstants.Groups.PROGRAM_ID) }
        assertNull(fetched)
    }

    @Test
    fun `delete GRADE group with members throws conflict`() = runTest {
        // GRADE is a required fixed slot; deleting one with members would leave the
        // attached students in an invalid state, so the service must refuse.
        assertFailsWith<ConflictException> {
            @OptIn(Transactional::class)
            groupService.delete(TestConstants.Groups.GRADE_ID)
        }

        // The group is still present.
        val fetched = transaction { groupService.getById(TestConstants.Groups.GRADE_ID) }
        assertNotNull(fetched)
    }

    @Test
    fun `delete ROOM group with members throws conflict`() = runTest {
        assertFailsWith<ConflictException> {
            @OptIn(Transactional::class)
            groupService.delete(TestConstants.Groups.ROOM_ID)
        }

        val fetched = transaction { groupService.getById(TestConstants.Groups.ROOM_ID) }
        assertNotNull(fetched)
    }

    @Test
    fun `delete empty GRADE group succeeds`() = runTest {
        val tempId = 700
        @OptIn(Transactional::class)
        groupService.create(tempId, "Grade 8", GroupType.GRADE)

        @OptIn(Transactional::class)
        groupService.delete(tempId)

        val fetched = transaction { groupService.getById(tempId) }
        assertNull(fetched)
    }

    @Test
    fun `delete empty ROOM group succeeds`() = runTest {
        val tempId = 701
        @OptIn(Transactional::class)
        groupService.create(tempId, "Room 8/1", GroupType.ROOM)

        @OptIn(Transactional::class)
        groupService.delete(tempId)

        val fetched = transaction { groupService.getById(tempId) }
        assertNull(fetched)
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

    @Test
    fun `delete group members removes users`() = runTest {
        val usersService: UsersService by application.dependencies

        @OptIn(Transactional::class)
        groupService.deleteMembers(TestConstants.Groups.GROUP_1_ID)

        val count = transaction { groupService.getMemberCount(TestConstants.Groups.GROUP_1_ID) }
        assertEquals(0, count)

        val john = transaction { usersService.getStudentById(TestConstants.Students.JOHN_ID) }
        assertNull(john)

        // The group itself is preserved.
        val group = transaction { groupService.getById(TestConstants.Groups.GROUP_1_ID) }
        assertNotNull(group)

        // Members of unrelated groups are untouched.
        val jane = transaction { usersService.getStudentById(TestConstants.Students.JANE_ID) }
        assertNotNull(jane)
    }

    @Test
    fun `delete group members on empty group succeeds`() = runTest {
        val tempId = 800
        @OptIn(Transactional::class)
        groupService.create(tempId, "Empty Group")

        @OptIn(Transactional::class)
        groupService.deleteMembers(tempId)

        val group = transaction { groupService.getById(tempId) }
        assertNotNull(group)
    }

    @Test
    fun `delete group members not found`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            @OptIn(Transactional::class)
            groupService.deleteMembers(UNUSED_ID)
        }
    }

    @Test
    fun `migrate group members moves memberships`() = runTest {
        // John is in GROUP_1 (CUSTOM). Migrate him into GROUP_2 (also CUSTOM).
        @OptIn(Transactional::class)
        groupService.migrateMembers(TestConstants.Groups.GROUP_1_ID, TestConstants.Groups.GROUP_2_ID)

        val sourceCount = transaction { groupService.getMemberCount(TestConstants.Groups.GROUP_1_ID) }
        assertEquals(0, sourceCount)

        val (targetMembers, _) = @OptIn(Transactional::class)
        groupService.getMembers(TestConstants.Groups.GROUP_2_ID)
        assertTrue(targetMembers.any { it.id.value == TestConstants.Students.JOHN_ID })
        assertTrue(targetMembers.any { it.id.value == TestConstants.Students.JANE_ID })
    }

    @Test
    fun `migrate group members preserves existing memberships in target`() = runTest {
        // GRADE_ID has both John and Jane. Create another empty GRADE group and migrate into it.
        val newGradeId = 801
        @OptIn(Transactional::class)
        groupService.create(newGradeId, "Grade 8", GroupType.GRADE)

        @OptIn(Transactional::class)
        groupService.migrateMembers(TestConstants.Groups.GRADE_ID, newGradeId)

        assertEquals(0, transaction { groupService.getMemberCount(TestConstants.Groups.GRADE_ID) })
        assertEquals(2, transaction { groupService.getMemberCount(newGradeId) })
    }

    @Test
    fun `migrate group members source not found`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            @OptIn(Transactional::class)
            groupService.migrateMembers(UNUSED_ID, TestConstants.Groups.GROUP_2_ID)
        }
    }

    @Test
    fun `migrate group members target not found`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            @OptIn(Transactional::class)
            groupService.migrateMembers(TestConstants.Groups.GROUP_1_ID, UNUSED_ID)
        }
    }

    @Test
    fun `migrate group members to same group throws conflict`() = runTest {
        assertFailsWith<ConflictException> {
            @OptIn(Transactional::class)
            groupService.migrateMembers(TestConstants.Groups.GROUP_1_ID, TestConstants.Groups.GROUP_1_ID)
        }
    }

    @Test
    fun `migrate group members across different types throws conflict`() = runTest {
        // GROUP_1 is CUSTOM, GRADE_ID is GRADE.
        assertFailsWith<ConflictException> {
            @OptIn(Transactional::class)
            groupService.migrateMembers(TestConstants.Groups.GROUP_1_ID, TestConstants.Groups.GRADE_ID)
        }
    }

    @Test
    fun `migrate group members from empty group succeeds`() = runTest {
        val tempId = 802
        @OptIn(Transactional::class)
        groupService.create(tempId, "Empty Source")

        @OptIn(Transactional::class)
        groupService.migrateMembers(tempId, TestConstants.Groups.GROUP_2_ID)

        // Jane was the only existing member of GROUP_2.
        assertEquals(1, transaction { groupService.getMemberCount(TestConstants.Groups.GROUP_2_ID) })
    }
}
