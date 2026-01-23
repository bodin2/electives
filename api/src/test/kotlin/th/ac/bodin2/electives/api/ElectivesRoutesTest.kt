package th.ac.bodin2.electives.api

import io.ktor.client.request.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.api.services.TestElectiveService
import th.ac.bodin2.electives.api.services.TestServiceConstants.ELECTIVE_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.ELECTIVE_WITHOUT_SUBJECTS_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.PASSWORD
import th.ac.bodin2.electives.api.services.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.TEACHER_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.proto.api.Elective
import th.ac.bodin2.electives.proto.api.ElectivesService
import th.ac.bodin2.electives.proto.api.Subject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElectivesRoutesTest : ApplicationTest() {
    private val ApplicationTestBuilder.usersService: UsersService
        get() {
            val service: UsersService by application.dependencies
            return service
        }

    private suspend fun ApplicationTestBuilder.studentToken(): String {
        startApplication()
        @OptIn(CreatesTransaction::class)
        return usersService.createSession(
            STUDENT_ID,
            PASSWORD,
            ""
        )
    }

    @Test
    fun `get electives list`() = runRouteTest {
        val response = client.get("/electives")
            .assertOK()
            .parse<ElectivesService.ListResponse>()

        assertEquals(response.electivesCount, TestElectiveService.ELECTIVE_IDS.size)

        for (electiveId in TestElectiveService.ELECTIVE_IDS) {
            assertTrue(response.electivesList.any { it.id == electiveId })
        }
    }

    @Test
    fun `get elective by id`() = runRouteTest {
        val elective = client.get("/electives/${ELECTIVE_ID}")
            .assertOK()
            .parse<Elective>()

        assertEquals(ELECTIVE_ID, elective.id)
    }

    @Test
    fun `get elective not found`() = runRouteTest {
        client.get("/electives/${UNUSED_ID}").assertNotFound()
    }

    @Test
    fun `get elective subjects`() = runRouteTest {
        val response = client.get("/electives/${ELECTIVE_ID}/subjects")
            .assertOK()
            .parse<ElectivesService.ListSubjectsResponse>()

        assertEquals(SUBJECT_ID, response.subjectsList[0].id)
    }

    @Test
    fun `get elective subjects not found`() = runRouteTest {
        client.get("/electives/${UNUSED_ID}/subjects").assertNotFound()
    }

    @Test
    fun `get elective subject`() = runRouteTest {
        val subject = client.get("/electives/${ELECTIVE_ID}/subjects/${SUBJECT_ID}")
            .assertOK()
            .parse<Subject>()

        assertEquals(SUBJECT_ID, subject.id)
    }

    @Test
    fun `get elective subject not found`() = runRouteTest {
        client.get("/electives/${ELECTIVE_WITHOUT_SUBJECTS_ID}/subjects/${UNUSED_ID}").assertNotFound()
    }

    @Test
    fun `get elective subject members without auth`() = runRouteTest {
        client.get("/electives/${ELECTIVE_ID}/subjects/${SUBJECT_ID}/members").assertUnauthorized()
    }

    @Test
    fun `get elective subject members without students`() = runRouteTest {
        val response = client.getWithAuth(
            "/electives/${ELECTIVE_ID}/subjects/${SUBJECT_ID}/members?with_students=false",
            studentToken()
        )
            .assertOK()
            .parse<ElectivesService.ListSubjectMembersResponse>()

        assertEquals(0, response.studentsCount)
        assertEquals(TEACHER_ID, response.teachersList[0].id)
    }

    @Test
    fun `get elective subject members with students`() = runRouteTest {
        val response = client.getWithAuth(
            "/electives/${ELECTIVE_ID}/subjects/${SUBJECT_ID}/members?with_students=true",
            studentToken()
        )
            .assertOK()
            .parse<ElectivesService.ListSubjectMembersResponse>()

        assertEquals(TEACHER_ID, response.teachersList[0].id)
        assertEquals(STUDENT_ID, response.studentsList[0].id)
    }

    @Test
    fun `get elective subject members in not found elective`() = runRouteTest {
        client.getWithAuth("/electives/${UNUSED_ID}/subjects/${SUBJECT_ID}/members", studentToken()).assertNotFound()
    }

    @Test
    fun `get elective subject members without query param`() = runRouteTest {
        // Should default to false
        val response = client.getWithAuth("/electives/${ELECTIVE_ID}/subjects/${SUBJECT_ID}/members", studentToken())
            .assertOK()
            .parse<ElectivesService.ListSubjectMembersResponse>()

        assertEquals(0, response.studentsCount)
    }

    @Test
    fun `get elective subject members with invalid query param`() = runRouteTest {
        // Should coerce to false with Ktor's data conversion service
        val response = client.getWithAuth(
            "/electives/${ELECTIVE_ID}/subjects/${SUBJECT_ID}/members?with_students=coerced",
            studentToken()
        )
            .assertOK()
            .parse<ElectivesService.ListSubjectMembersResponse>()

        assertEquals(0, response.studentsCount)
    }
}
