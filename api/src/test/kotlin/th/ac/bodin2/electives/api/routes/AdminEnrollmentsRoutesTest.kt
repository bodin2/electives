package th.ac.bodin2.electives.api.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.getWithAuth
import th.ac.bodin2.electives.api.parse
import th.ac.bodin2.electives.api.patchProtoWithAuth
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ADMIN_TOKEN
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.proto.api.AdminService
import th.ac.bodin2.electives.proto.api.AdminServiceKt.enrollmentPatch
import th.ac.bodin2.electives.proto.api.Enrollment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminEnrollmentsRoutesTest : ApplicationTest() {
    private suspend fun HttpClient.adminGet(url: String): HttpResponse =
        getWithAuth(url, ADMIN_TOKEN)

    @Test
    fun `get enrollment subjects not found`() = runRouteTest {
        startApplication()

        client.adminGet("/admin/enrollments/$UNUSED_ID/subjects").assertNotFound()
    }

    @Test
    fun `delete enrollment without auth returns unauthorized`() = runRouteTest {
        client.delete("/admin/enrollments/$ENROLLMENT_ID").assertUnauthorized()
    }

    @Test
    fun `patch enrollment without auth returns unauthorized`() = runRouteTest {
        client.patch("/admin/enrollments/$ENROLLMENT_ID").assertUnauthorized()
    }

    @Test
    fun `patch enrollment returns updated enrollment`() = runRouteTest {
        startApplication()

        val enrollment = client.patchProtoWithAuth(
            "/admin/enrollments/$ENROLLMENT_ID",
            enrollmentPatch { name = "New Name" },
            ADMIN_TOKEN
        ).assertOK().parse<Enrollment>()

        assertEquals(ENROLLMENT_ID, enrollment.id)
    }

    @Test
    fun `patch enrollment not found`() = runRouteTest {
        startApplication()

        client.patchProtoWithAuth(
            "/admin/enrollments/$UNUSED_ID",
            enrollmentPatch { name = "New Name" },
            ADMIN_TOKEN
        ).assertNotFound("Enrollment not found")
    }

    @Test
    fun `get enrollments progress`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/enrollments/progress?ids=$ENROLLMENT_ID")
            .assertOK()
            .parse<AdminService.ListEnrollmentsEnrolledCounts>()

        assertTrue(response.countsMap.containsKey(ENROLLMENT_ID))
        assertEquals(0, response.countsMap[ENROLLMENT_ID]!!.selected)
    }

    @Test
    fun `get enrollments progress skips non-existent enrollments`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/enrollments/progress?ids=$ENROLLMENT_ID,$UNUSED_ID")
            .assertOK()
            .parse<AdminService.ListEnrollmentsEnrolledCounts>()

        assertTrue(response.countsMap.containsKey(ENROLLMENT_ID))
        assertFalse(response.countsMap.containsKey(UNUSED_ID))
    }

    @Test
    fun `get enrollments progress with invalid ids returns bad request`() = runRouteTest {
        startApplication()

        client.adminGet("/admin/enrollments/progress?ids=abc").assertBadRequest()
    }

    @Test
    fun `get enrollments progress without auth returns unauthorized`() = runRouteTest {
        client.get("/admin/enrollments/progress?ids=$ENROLLMENT_ID").assertUnauthorized()
    }
}
