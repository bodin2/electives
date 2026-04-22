package th.ac.bodin2.electives.api.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.deleteWithAuth
import th.ac.bodin2.electives.api.getWithAuth
import th.ac.bodin2.electives.api.parse
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ADMIN_TOKEN
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.proto.api.AdminService
import kotlin.test.Test
import kotlin.test.assertTrue

class AdminUsersRoutesTest : ApplicationTest() {
    private suspend fun HttpClient.adminGet(url: String): HttpResponse =
        getWithAuth(url, ADMIN_TOKEN)

    private suspend fun HttpClient.adminDelete(url: String): HttpResponse =
        deleteWithAuth(url, ADMIN_TOKEN)

    @Test
    fun `get user not found`() = runRouteTest {
        startApplication()

        client.adminGet("/admin/users/$UNUSED_ID").assertNotFound()
    }

    @Test
    fun `get students list`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/users/students")
            .assertOK()
            .parse<AdminService.ListUsersResponse>()

        assertTrue(response.usersCount >= 0)
    }

    @Test
    fun `get teachers list`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/users/teachers")
            .assertOK()
            .parse<AdminService.ListUsersResponse>()

        assertTrue(response.usersCount >= 0)
    }

    @Test
    fun `delete user without auth returns unauthorized`() = runRouteTest {
        client.delete("/admin/users/$STUDENT_ID").assertUnauthorized()
    }
}
