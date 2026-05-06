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
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.api.services.mock.TestSubjectService
import th.ac.bodin2.electives.proto.api.AdminService
import th.ac.bodin2.electives.proto.api.ElectivesService
import th.ac.bodin2.electives.proto.api.Subject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class AdminSubjectsRoutesTest : ApplicationTest() {
    private suspend fun HttpClient.adminGet(url: String): HttpResponse =
        getWithAuth(url, ADMIN_TOKEN)

    @Test
    fun `get subjects without auth returns unauthorized`() = runRouteTest {
        client.get("/admin/subjects").assertUnauthorized()
    }

    @Test
    fun `get subjects list`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/subjects")
            .assertOK()
            .parse<ElectivesService.ListSubjectsResponse>()

        assertEquals(TestSubjectService.SUBJECT_IDS.size, response.subjects.size)
        assertEquals(0, response.subjects[0].teachers.size)
    }

    @Test
    fun `get subject by id`() = runRouteTest {
        startApplication()

        val subject = client.adminGet("/admin/subjects/$SUBJECT_ID")
            .assertOK()
            .parse<Subject>()

        assertEquals(SUBJECT_ID.toInt(), subject.id)
        assertEquals(0, subject.teachers.size)
    }

    @Test
    fun `get subject not found`() = runRouteTest {
        startApplication()

        client.adminGet("/admin/subjects/$UNUSED_ID").assertNotFound()
    }

    @Test
    fun `delete subject without auth returns unauthorized`() = runRouteTest {
        client.delete("/admin/subjects/$SUBJECT_ID").assertUnauthorized()
    }

    @Test
    fun `get elective IDs for subject`() = runRouteTest {
        startApplication()

        val ids = client.adminGet("/admin/subjects/$SUBJECT_ID/elective-ids")
            .assertOK()
            .parse<AdminService.SubjectElectiveIds>()
        assertEquals(1, ids.elective_ids.size)
        assertContains(ids.elective_ids, ELECTIVE_ID.toInt())
    }

    @Test
    fun `get elective IDs for not found subject`() = runRouteTest {
        startApplication()

        client.adminGet("/admin/subjects/$UNUSED_ID").assertNotFound()
    }

    @Test
    fun `patch subject without auth returns unauthorized`() = runRouteTest {
        client.patch("/admin/subjects/$SUBJECT_ID").assertUnauthorized()
    }

    @Test
    fun `patch subject returns updated subject`() = runRouteTest {
        startApplication()

        val subject = client.patchProtoWithAuth(
            "/admin/subjects/$SUBJECT_ID",
            AdminService.SubjectPatch(name = "New Name"),
            ADMIN_TOKEN
        ).assertOK().parse<Subject>()

        assertEquals(SUBJECT_ID.toInt(), subject.id)
    }

    @Test
    fun `patch subject not found`() = runRouteTest {
        startApplication()

        client.patchProtoWithAuth(
            "/admin/subjects/$UNUSED_ID",
            AdminService.SubjectPatch(name = "New Name"),
            ADMIN_TOKEN
        ).assertNotFound("Subject not found")
    }
}
