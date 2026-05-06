package th.ac.bodin2.electives.api.routes

import io.ktor.client.request.*
import io.ktor.http.*
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.deleteWithAuth
import th.ac.bodin2.electives.api.getWithAuth
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ADMIN_TOKEN
import kotlin.test.Test

/**
 * Tests that routes correctly handle UInt IDs:
 * - IDs > Int.MAX_VALUE (2147483647) should be accepted and routed normally
 * - Negative IDs should be rejected (cannot parse as UInt)
 */
class IdBoundaryTest : ApplicationTest() {
    companion object {
        // 3000000000 > Int.MAX_VALUE (2147483647), valid UInt
        const val LARGE_ID = "3000000000"

        const val NEGATIVE_ID = "-1"
    }

    @Test
    fun `elective route accepts id greater than Int MAX_VALUE`() = runRouteTest {
        // Should get 404 (not found), not a parse error
        client.get("/electives/$LARGE_ID").assertNotFound()
    }

    @Test
    fun `elective route rejects negative id`() = runRouteTest {
        assertIdRejected(client.get("/electives/$NEGATIVE_ID").status)
    }

    @Test
    fun `elective subjects route accepts id greater than Int MAX_VALUE`() = runRouteTest {
        client.get("/electives/$LARGE_ID/subjects").assertNotFound()
    }

    @Test
    fun `elective subjects route rejects negative id`() = runRouteTest {
        assertIdRejected(client.get("/electives/$NEGATIVE_ID/subjects").status)
    }

    @Test
    fun `elective subject route accepts ids greater than Int MAX_VALUE`() = runRouteTest {
        client.get("/electives/$LARGE_ID/subjects/$LARGE_ID").assertNotFound()
    }

    @Test
    fun `elective subject route rejects negative elective id`() = runRouteTest {
        assertIdRejected(client.get("/electives/$NEGATIVE_ID/subjects/1").status)
    }

    @Test
    fun `elective subject route rejects negative subject id`() = runRouteTest {
        assertIdRejected(client.get("/electives/1/subjects/$NEGATIVE_ID").status)
    }

    @Test
    fun `admin team route accepts id greater than Int MAX_VALUE`() = runRouteTest {
        startApplication()
        client.getWithAuth("/admin/teams/$LARGE_ID", ADMIN_TOKEN).assertNotFound()
    }

    @Test
    fun `admin team route rejects negative id`() = runRouteTest {
        startApplication()
        assertIdRejected(client.getWithAuth("/admin/teams/$NEGATIVE_ID", ADMIN_TOKEN).status)
    }

    @Test
    fun `admin subject route accepts id greater than Int MAX_VALUE`() = runRouteTest {
        startApplication()
        client.getWithAuth("/admin/subjects/$LARGE_ID", ADMIN_TOKEN).assertNotFound()
    }

    @Test
    fun `admin subject route rejects negative id`() = runRouteTest {
        startApplication()
        assertIdRejected(client.getWithAuth("/admin/subjects/$NEGATIVE_ID", ADMIN_TOKEN).status)
    }

    @Test
    fun `admin elective route accepts id greater than Int MAX_VALUE`() = runRouteTest {
        startApplication()
        client.getWithAuth("/admin/electives/$LARGE_ID", ADMIN_TOKEN).assertNotFound()
    }

    @Test
    fun `admin elective route rejects negative id`() = runRouteTest {
        startApplication()
        assertIdRejected(client.deleteWithAuth("/admin/electives/$NEGATIVE_ID", ADMIN_TOKEN).status)
    }

    @Test
    fun `admin user route accepts id greater than Int MAX_VALUE`() = runRouteTest {
        startApplication()
        client.getWithAuth("/admin/users/$LARGE_ID", ADMIN_TOKEN).assertNotFound()
    }

    @Test
    fun `admin user route rejects negative id`() = runRouteTest {
        startApplication()
        assertIdRejected(client.deleteWithAuth("/admin/users/$NEGATIVE_ID", ADMIN_TOKEN).status)
    }

    private fun assertIdRejected(status: HttpStatusCode) {
        // Ktor returns 400 when a path parameter fails type conversion,
        // or 404 if the route simply doesn't match the negative segment
        assert(status == HttpStatusCode.BadRequest || status == HttpStatusCode.NotFound) {
            "Expected 400 or 404 for negative ID, got $status"
        }
    }
}
