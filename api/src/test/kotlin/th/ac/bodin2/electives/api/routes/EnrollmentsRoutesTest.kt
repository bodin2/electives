package th.ac.bodin2.electives.api.routes

import io.ktor.client.request.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.getWithAuth
import th.ac.bodin2.electives.api.parse
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.services.mock.TestEnrollmentService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_WITHOUT_SUBJECTS_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.PASSWORD
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.TEACHER_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.proto.api.Enrollment
import th.ac.bodin2.electives.proto.api.EnrollmentsService
import th.ac.bodin2.electives.proto.api.Subject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnrollmentsRoutesTest : ApplicationTest() {
    private val ApplicationTestBuilder.usersService: UsersService
        get() {
            val service: UsersService by application.dependencies
            return service
        }

    private suspend fun ApplicationTestBuilder.studentToken(): String {
        startApplication()
        @OptIn(Transactional::class)
        return usersService.createSession(
            STUDENT_ID,
            PASSWORD,
            ""
        )
    }

    @Test
    fun `get enrollments list`() = runRouteTest {
        val response = client.get("/enrollments")
            .assertOK()
            .parse<EnrollmentsService.ListResponse>()

        assertEquals(response.enrollmentsCount, TestEnrollmentService.ENROLLMENT_IDS.size)

        for (enrollmentId in TestEnrollmentService.ENROLLMENT_IDS) {
            assertTrue(response.enrollmentsList.any { it.id == enrollmentId })
        }
    }

    @Test
    fun `get enrollment by id`() = runRouteTest {
        val enrollment = client.get("/enrollments/${ENROLLMENT_ID}")
            .assertOK()
            .parse<Enrollment>()

        assertEquals(ENROLLMENT_ID, enrollment.id)
    }

    @Test
    fun `get enrollment not found`() = runRouteTest {
        client.get("/enrollments/${UNUSED_ID}").assertNotFound()
    }

    @Test
    fun `get enrollment subjects`() = runRouteTest {
        val response = client.get("/enrollments/${ENROLLMENT_ID}/subjects")
            .assertOK()
            .parse<EnrollmentsService.ListSubjectsResponse>()

        assertEquals(SUBJECT_ID, response.subjectsList[0].id)
        assertEquals(TEACHER_ID, response.subjectsList[0].teachersList[0].id)
        assertTrue(response.subjectsList[0].hasEnrolledCount())
        assertEquals(0, response.subjectsList[0].enrolledCount)
    }

    @Test
    fun `get enrollment subjects not found`() = runRouteTest {
        client.get("/enrollments/${UNUSED_ID}/subjects").assertNotFound()
    }

    @Test
    fun `get enrollment subject`() = runRouteTest {
        val subject = client.get("/enrollments/${ENROLLMENT_ID}/subjects/${SUBJECT_ID}")
            .assertOK()
            .parse<Subject>()

        assertEquals(SUBJECT_ID, subject.id)
        assertEquals(TEACHER_ID, subject.teachersList[0].id)
        assertTrue(subject.hasEnrolledCount())
        assertEquals(0, subject.enrolledCount)
    }

    @Test
    fun `get enrollment subject not found`() = runRouteTest {
        client.get("/enrollments/${ENROLLMENT_WITHOUT_SUBJECTS_ID}/subjects/${UNUSED_ID}").assertNotFound()
    }

    @Test
    fun `get enrollment subject members without auth`() = runRouteTest {
        client.get("/enrollments/${ENROLLMENT_ID}/subjects/${SUBJECT_ID}/members").assertUnauthorized()
    }

    @Test
    fun `get enrollment subject members without students`() = runRouteTest {
        val response = client.getWithAuth(
            "/enrollments/${ENROLLMENT_ID}/subjects/${SUBJECT_ID}/members?with_students=false",
            studentToken()
        )
            .assertOK()
            .parse<EnrollmentsService.ListSubjectMembersResponse>()

        assertEquals(0, response.studentsCount)
        assertEquals(TEACHER_ID, response.teachersList[0].id)
    }

    @Test
    fun `get enrollment subject members with students`() = runRouteTest {
        val response = client.getWithAuth(
            "/enrollments/${ENROLLMENT_ID}/subjects/${SUBJECT_ID}/members?with_students=true",
            studentToken()
        )
            .assertOK()
            .parse<EnrollmentsService.ListSubjectMembersResponse>()

        assertEquals(TEACHER_ID, response.teachersList[0].id)
        assertEquals(STUDENT_ID, response.studentsList[0].id)
    }

    @Test
    fun `get enrollment subject members in not found enrollment`() = runRouteTest {
        client.getWithAuth("/enrollments/${UNUSED_ID}/subjects/${SUBJECT_ID}/members", studentToken()).assertNotFound()
    }

    @Test
    fun `get enrollment subject members without query param`() = runRouteTest {
        // Should default to false
        val response =
            client.getWithAuth("/enrollments/${ENROLLMENT_ID}/subjects/${SUBJECT_ID}/members", studentToken())
                .assertOK()
                .parse<EnrollmentsService.ListSubjectMembersResponse>()

        assertEquals(0, response.studentsCount)
    }

    @Test
    fun `get enrollment subject members with invalid query param`() = runRouteTest {
        // Should coerce to false with Ktor's data conversion service
        val response = client.getWithAuth(
            "/enrollments/${ENROLLMENT_ID}/subjects/${SUBJECT_ID}/members?with_students=coerced",
            studentToken()
        )
            .assertOK()
            .parse<EnrollmentsService.ListSubjectMembersResponse>()

        assertEquals(0, response.studentsCount)
    }

    @Test
    fun `get enrollment unenrolled members without auth`() = runRouteTest {
        client.get("/enrollments/${ENROLLMENT_ID}/unenrolled-members?group=1").assertUnauthorized()
    }

    @Test
    fun `get enrollment unenrolled members with student auth`() = runRouteTest {
        client.getWithAuth("/enrollments/${ENROLLMENT_ID}/unenrolled-members?group=1", studentToken())
            .assertUnauthorized()
    }

    @Test
    fun `get enrollment unenrolled members with teacher auth`() = runRouteTest {
        startApplication()
        @OptIn(Transactional::class)
        val token = usersService.createSession(TEACHER_ID, PASSWORD, "")

        val response = client.getWithAuth("/enrollments/${ENROLLMENT_ID}/unenrolled-members?group=1", token)
            .assertOK()
            .parse<th.ac.bodin2.electives.proto.api.AdminService.ListUsersResponse>()

        assertEquals(1, response.total)
        assertEquals(STUDENT_ID, response.usersList[0].id)
    }
}
