package th.ac.bodin2.electives.api.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.getWithAuth
import th.ac.bodin2.electives.api.parse
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ADMIN_TOKEN
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ELECTIVE_TEAM_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.api.services.mock.TestTeamService
import th.ac.bodin2.electives.proto.api.AdminService
import th.ac.bodin2.electives.proto.api.Team
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminTeamsRoutesTest : ApplicationTest() {
    private suspend fun HttpClient.adminGet(url: String): HttpResponse =
        getWithAuth(url, ADMIN_TOKEN)

    @Test
    fun `get teams without auth returns unauthorized`() = runRouteTest {
        client.get("/admin/teams").assertUnauthorized()
    }

    @Test
    fun `get teams list`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/teams")
            .assertOK()
            .parse<AdminService.ListTeamsResponse>()

        assertEquals(TestTeamService.TEAM_IDS.size, response.teamsCount)
    }

    @Test
    fun `get team by id`() = runRouteTest {
        startApplication()

        val team = client.adminGet("/admin/teams/$ELECTIVE_TEAM_ID")
            .assertOK()
            .parse<Team>()

        assertEquals(ELECTIVE_TEAM_ID, team.id)
    }

    @Test
    fun `get team not found`() = runRouteTest {
        startApplication()

        client.adminGet("/admin/teams/$UNUSED_ID").assertNotFound()
    }

    @Test
    fun `delete team without auth returns unauthorized`() = runRouteTest {
        client.delete("/admin/teams/$ELECTIVE_TEAM_ID").assertUnauthorized()
    }

    @Test
    fun `get team member counts without auth returns unauthorized`() = runRouteTest {
        client.get("/admin/teams/member-counts").assertUnauthorized()
    }

    @Test
    fun `get team member counts`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/teams/member-counts")
            .assertOK()
            .parse<AdminService.TeamMemberCounts>()

        assertEquals(TestTeamService.TEAM_IDS.size, response.memberCountsCount)
        TestTeamService.TEAM_IDS.forEach { teamId ->
            assertEquals(0, response.memberCountsMap[teamId])
        }
    }

    @Test
    fun `get team members without auth returns unauthorized`() = runRouteTest {
        client.get("/admin/teams/$ELECTIVE_TEAM_ID/members").assertUnauthorized()
    }

    @Test
    fun `get team members`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/teams/$ELECTIVE_TEAM_ID/members")
            .assertOK()
            .parse<AdminService.ListUsersResponse>()

        assertEquals(0, response.total)
        assertEquals(0, response.usersCount)
    }

    @Test
    fun `get team members not found`() = runRouteTest {
        startApplication()

        client.adminGet("/admin/teams/$UNUSED_ID/members").assertNotFound()
    }
}
