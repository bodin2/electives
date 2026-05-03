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
import th.ac.bodin2.electives.proto.api.AdminServiceKt.electivePatch
import th.ac.bodin2.electives.proto.api.Elective
import kotlin.test.Test
import kotlin.test.assertEquals

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
            electivePatch { name = "New Name" },
            ADMIN_TOKEN
        ).assertOK().parse<Elective>()

        assertEquals(ELECTIVE_ID, elective.id)
    }

    @Test
    fun `patch elective not found`() = runRouteTest {
        startApplication()

        client.patchProtoWithAuth(
            "/admin/electives/$UNUSED_ID",
            electivePatch { name = "New Name" },
            ADMIN_TOKEN
        ).assertNotFound("Elective not found")
    }
}
