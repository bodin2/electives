package th.ac.bodin2.electives.api

import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.services.TeamService
import th.ac.bodin2.electives.api.services.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

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
        @OptIn(CreatesTransaction::class)
        val created = teamService.create(newId, "New Team")

        assertEquals(newId, created.id.value)

        val fetched = transaction { teamService.getById(newId) }
        assertNotNull(fetched)
        assertEquals("New Team", fetched.name)
    }

    @Test
    fun `delete team`() = runTest {
        // Create a team that's not referenced by any subjects/electives
        val tempId = 600
        @OptIn(CreatesTransaction::class)
        teamService.create(tempId, "Temporary Team")

        @OptIn(CreatesTransaction::class)
        teamService.delete(tempId)

        val fetched = transaction { teamService.getById(tempId) }
        assertNull(fetched)
    }

    @Test
    fun `delete team not found`() = runTest {
        assertFailsWith<NotFoundException> {
            @OptIn(CreatesTransaction::class)
            teamService.delete(UNUSED_ID)
        }
    }

    @Test
    fun `update team name`() = runTest {
        @OptIn(CreatesTransaction::class)
        teamService.update(
            TestConstants.Teams.TEAM_1_ID,
            TeamService.TeamUpdate(name = "Updated Team")
        )

        val fetched = transaction { teamService.getById(TestConstants.Teams.TEAM_1_ID) }
        assertNotNull(fetched)
        assertEquals("Updated Team", fetched.name)
    }

    @Test
    fun `update team not found`() = runTest {
        assertFailsWith<NotFoundException> {
            @OptIn(CreatesTransaction::class)
            teamService.update(
                UNUSED_ID,
                TeamService.TeamUpdate(name = "Does not exist")
            )
        }
    }
}
