package th.ac.bodin2.electives.api

import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals

class MiscRoutesTest : ApplicationTest() {
    @Test
    fun `status endpoint`() = runRouteTest {
        client.head("/status").assertOK()
    }

    @Test
    fun `status endpoint with get`() = runRouteTest {
        val response = client.get("/status")
        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
    }

    @Test
    fun `non existent route`() = runRouteTest {
        client.get("/nonexistent").assertNotFound()
    }
}
