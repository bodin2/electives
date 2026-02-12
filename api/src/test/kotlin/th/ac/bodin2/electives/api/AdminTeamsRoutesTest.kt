package th.ac.bodin2.electives.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.mockk.every
import th.ac.bodin2.electives.api.services.AdminAuthService
import th.ac.bodin2.electives.api.services.TestServiceConstants.ELECTIVE_TEAM_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.api.services.TestTeamService
import th.ac.bodin2.electives.proto.api.AdminService
import th.ac.bodin2.electives.proto.api.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminTeamsRoutesTest : ApplicationTest() {
    private val ApplicationTestBuilder.adminAuthService: AdminAuthService
        get() {
            val service: AdminAuthService by application.dependencies
            return service
        }

    private fun ApplicationTestBuilder.enableAdminAuth() {
        every { adminAuthService.hasSession(any(), any()) } returns true
    }

    private suspend fun HttpClient.adminGet(url: String): HttpResponse =
        getWithAuth(url, "admin-token")

    @Test
    fun `get teams without auth returns unauthorized`() = runRouteTest {
        client.get("/admin/teams").assertUnauthorized()
    }

    @Test
    fun `get teams list`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        val response = client.adminGet("/admin/teams")
            .assertOK()
            .parse<AdminService.ListTeamsResponse>()

        assertEquals(TestTeamService.TEAM_IDS.size, response.teamsCount)
    }

    @Test
    fun `get team by id`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        val team = client.adminGet("/admin/teams/$ELECTIVE_TEAM_ID")
            .assertOK()
            .parse<Team>()

        assertEquals(ELECTIVE_TEAM_ID, team.id)
    }

    @Test
    fun `get team not found`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        client.adminGet("/admin/teams/$UNUSED_ID").assertNotFound()
    }

    @Test
    fun `delete team without auth returns unauthorized`() = runRouteTest {
        client.delete("/admin/teams/$ELECTIVE_TEAM_ID").assertUnauthorized()
    }
}
