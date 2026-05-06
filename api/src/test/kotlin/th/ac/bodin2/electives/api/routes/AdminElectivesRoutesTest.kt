package th.ac.bodin2.electives.api.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.getWithAuth
import th.ac.bodin2.electives.api.parse
import th.ac.bodin2.electives.api.patchProtoWithAuth
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ADMIN_TOKEN
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ELECTIVE_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.proto.api.AdminService
import th.ac.bodin2.electives.proto.api.Elective
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `patch elective without auth returns unauthorized`() = runRouteTest {
        client.patch("/admin/electives/$ELECTIVE_ID").assertUnauthorized()
    }

    @Test
    fun `patch elective returns updated elective`() = runRouteTest {
        startApplication()

        val elective = client.patchProtoWithAuth(
            "/admin/electives/$ELECTIVE_ID",
            AdminService.ElectivePatch(name = "New Name"),
            ADMIN_TOKEN
        ).assertOK().parse<Elective>()

        assertEquals(ELECTIVE_ID.toInt(), elective.id)
    }

    @Test
    fun `patch elective not found`() = runRouteTest {
        startApplication()

        client.patchProtoWithAuth(
            "/admin/electives/$UNUSED_ID",
            AdminService.ElectivePatch(name = "New Name"),
            ADMIN_TOKEN
        ).assertNotFound("Elective not found")
    }

    @Test
    fun `get electives progress`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/electives/progress?ids=$ELECTIVE_ID")
            .assertOK()
            .parse<AdminService.ListElectivesEnrolledCounts>()

        assertTrue(response.counts.containsKey(ELECTIVE_ID.toInt()))
        assertEquals(0, response.counts[ELECTIVE_ID.toInt()]!!.selected)
    }

    @Test
    fun `get electives progress skips non-existent electives`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/electives/progress?ids=$ELECTIVE_ID,$UNUSED_ID")
            .assertOK()
            .parse<AdminService.ListElectivesEnrolledCounts>()

        assertTrue(response.counts.containsKey(ELECTIVE_ID.toInt()))
        assertFalse(response.counts.containsKey(UNUSED_ID.toInt()))
    }

    @Test
    fun `get electives progress with invalid ids returns bad request`() = runRouteTest {
        startApplication()

        client.adminGet("/admin/electives/progress?ids=abc").assertBadRequest()
    }

    @Test
    fun `get electives progress without auth returns unauthorized`() = runRouteTest {
        client.get("/admin/electives/progress?ids=$ELECTIVE_ID").assertUnauthorized()
    }
}
