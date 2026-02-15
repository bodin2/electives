package th.ac.bodin2.electives.api.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.mockk.every
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.getWithAuth
import th.ac.bodin2.electives.api.parse
import th.ac.bodin2.electives.api.services.AdminAuthService
import th.ac.bodin2.electives.api.services.mock.TestElectiveService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ELECTIVE_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.proto.api.Elective
import th.ac.bodin2.electives.proto.api.ElectivesService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminElectivesRoutesTest : ApplicationTest() {
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
    fun `get electives without auth returns unauthorized`() = runRouteTest {
        client.get("/admin/electives").assertUnauthorized()
    }

    @Test
    fun `get electives list`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        val response = client.adminGet("/admin/electives")
            .assertOK()
            .parse<ElectivesService.ListResponse>()

        assertEquals(TestElectiveService.ELECTIVE_IDS.size, response.electivesCount)
    }

    @Test
    fun `get elective by id`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        val elective = client.adminGet("/admin/electives/$ELECTIVE_ID")
            .assertOK()
            .parse<Elective>()

        assertEquals(ELECTIVE_ID, elective.id)
    }

    @Test
    fun `get elective not found`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        client.adminGet("/admin/electives/$UNUSED_ID").assertNotFound()
    }

    @Test
    fun `get elective subjects`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        val response = client.adminGet("/admin/electives/$ELECTIVE_ID/subjects")
            .assertOK()
            .parse<ElectivesService.ListSubjectsResponse>()

        assertTrue(response.subjectsCount > 0)
    }

    @Test
    fun `get elective subjects not found`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        client.adminGet("/admin/electives/$UNUSED_ID/subjects").assertNotFound()
    }

    @Test
    fun `delete elective without auth returns unauthorized`() = runRouteTest {
        client.delete("/admin/electives/$ELECTIVE_ID").assertUnauthorized()
    }
}
