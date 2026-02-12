package th.ac.bodin2.electives.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.mockk.every
import th.ac.bodin2.electives.api.services.AdminAuthService
import th.ac.bodin2.electives.api.services.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.api.services.TestSubjectService
import th.ac.bodin2.electives.proto.api.ElectivesService
import th.ac.bodin2.electives.proto.api.Subject
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminSubjectsRoutesTest : ApplicationTest() {
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
    fun `get subjects without auth returns unauthorized`() = runRouteTest {
        client.get("/admin/subjects").assertUnauthorized()
    }

    @Test
    fun `get subjects list`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        val response = client.adminGet("/admin/subjects")
            .assertOK()
            .parse<ElectivesService.ListSubjectsResponse>()

        assertEquals(TestSubjectService.SUBJECT_IDS.size, response.subjectsCount)
    }

    @Test
    fun `get subject by id`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        val subject = client.adminGet("/admin/subjects/$SUBJECT_ID")
            .assertOK()
            .parse<Subject>()

        assertEquals(SUBJECT_ID, subject.id)
    }

    @Test
    fun `get subject not found`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        client.adminGet("/admin/subjects/$UNUSED_ID").assertNotFound()
    }

    @Test
    fun `delete subject without auth returns unauthorized`() = runRouteTest {
        client.delete("/admin/subjects/$SUBJECT_ID").assertUnauthorized()
    }
}
