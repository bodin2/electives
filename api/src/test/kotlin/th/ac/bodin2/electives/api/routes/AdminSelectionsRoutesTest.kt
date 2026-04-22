package th.ac.bodin2.electives.api.routes

import io.ktor.client.*
import io.ktor.client.statement.*
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.getWithAuth
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ADMIN_TOKEN

class AdminSelectionsRoutesTest : ApplicationTest() {
    private suspend fun HttpClient.adminGet(url: String): HttpResponse =
        getWithAuth(url, ADMIN_TOKEN)

    // @TODO: Add other tests here (GET routes has already been merged with users)
}
