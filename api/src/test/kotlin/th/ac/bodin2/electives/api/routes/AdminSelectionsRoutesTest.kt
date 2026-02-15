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
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.proto.api.UsersService.StudentSelections
import kotlin.test.Test
import kotlin.test.assertTrue

class AdminSelectionsRoutesTest : ApplicationTest() {
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
    fun `get student selections without auth returns unauthorized`() = runRouteTest {
        client.get("/admin/users/$STUDENT_ID/selections").assertUnauthorized()
    }

    @Test
    fun `get student selections`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        val response = client.adminGet("/admin/users/$STUDENT_ID/selections")
            .assertOK()
            .parse<StudentSelections>()

        assertTrue(response.subjectsCount >= 0)
    }

    @Test
    fun `get non-student selections returns bad request`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        client.adminGet("/admin/users/$UNUSED_ID/selections").assertBadRequest()
    }
}
