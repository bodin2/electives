package th.ac.bodin2.electives.api.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.getWithAuth
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ADMIN_TOKEN
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ELECTIVE_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import kotlin.test.Test

class AdminElectivesRoutesTest : ApplicationTest() {
    private suspend fun HttpClient.adminGet(url: String): HttpResponse =
        getWithAuth(url, ADMIN_TOKEN)

    @Test
    fun `get elective subjects not found`() = runRouteTest {
        startApplication()

        client.adminGet("/admin/electives/$UNUSED_ID/subjects").assertNotFound()
    }

    @Test
    fun `delete elective without auth returns unauthorized`() = runRouteTest {
        client.delete("/admin/electives/$ELECTIVE_ID").assertUnauthorized()
    }
}
