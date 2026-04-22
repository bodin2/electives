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

class TeamServiceImplTest : ApplicationTest() {
    private val ApplicationTestBuilder.teamService: TeamService
        get() {
            val service: TeamService by application.dependencies
            return service
        }

    @Test
    fun `get all teams`() = runTest {
        val teams = transaction { teamService.getAll() }
        assertEquals(2, teams.size)
    }

    @Test
    fun `get team by id`() = runTest {
        val team = transaction { teamService.getById(TestConstants.Teams.TEAM_1_ID) }
        assertNotNull(team)
        assertEquals(TestConstants.Teams.TEAM_1_ID, team.id.value)
    }

    @Test
    fun `get team not found`() = runTest {
        val team = transaction { teamService.getById(UNUSED_ID) }
        assertNull(team)
    }

    @Test
    fun `create team`() = runTest {
        val newId = 500

        @OptIn(Transactional::class)
        val created = teamService.create(newId, "New Team")

        assertEquals(newId, created.id.value)

        val fetched = transaction { teamService.getById(newId) }
        assertNotNull(fetched)
        assertEquals("New Team", fetched.name)
    }

    @Test
    fun `create team with duplicate id throws conflict`() = runTest {
        assertFailsWith<ConflictException> {
            @OptIn(Transactional::class)
            teamService.create(TestConstants.Teams.TEAM_1_ID, "Duplicate Team")
        }
    }

    @Test
    fun `delete team`() = runTest {
        // Create a team that's not referenced by any subjects/electives
        val tempId = 600
        @OptIn(Transactional::class)
        teamService.create(tempId, "Temporary Team")

        @OptIn(Transactional::class)
        teamService.delete(tempId)

        val fetched = transaction { teamService.getById(tempId) }
        assertNull(fetched)
    }

    @Test
    fun `delete team not found`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            @OptIn(Transactional::class)
            teamService.delete(UNUSED_ID)
        }
    }

    @Test
    fun `update team name`() = runTest {
        @OptIn(Transactional::class)
        teamService.update(
            TestConstants.Teams.TEAM_1_ID,
            TeamService.TeamUpdate(name = "Updated Team")
        )

        val fetched = transaction { teamService.getById(TestConstants.Teams.TEAM_1_ID) }
        assertNotNull(fetched)
        assertEquals("Updated Team", fetched.name)
    }

    @Test
    fun `update team nothing to update`() = runTest {
        assertFailsWith<NothingToUpdateException> {
            @OptIn(Transactional::class)
            teamService.update(
                TestConstants.Teams.TEAM_1_ID,
                TeamService.TeamUpdate()
            )
        }
    }

    @Test
    fun `update team not found`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            @OptIn(Transactional::class)
            teamService.update(
                UNUSED_ID,
                TeamService.TeamUpdate(name = "Does not exist")
            )
        }
    }

    @Test
    fun `get team member counts`() = runTest {
        val counts = transaction { teamService.getMemberCounts() }
        assertTrue(counts.containsKey(TestConstants.Teams.TEAM_1_ID))
        assertTrue(counts.containsKey(TestConstants.Teams.TEAM_2_ID))
    }

    @Test
    fun `get team members`() = runTest {
        val (members, count) = @OptIn(Transactional::class)
        teamService.getMembers(TestConstants.Teams.TEAM_1_ID)

        assertTrue(count > 0)
        assertEquals(count.toInt(), members.size)
    }

    @Test
    fun `get team members not found`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            @OptIn(Transactional::class)
            teamService.getMembers(UNUSED_ID)
        }
    }
}
