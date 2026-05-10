package th.ac.bodin2.electives.api.routes

import io.ktor.client.statement.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.*
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.EnrollmentSelectionService.*
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.PASSWORD
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.TEACHER_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.api.services.mock.testEnrollmentSelectionServiceResponse
import th.ac.bodin2.electives.proto.api.User
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.proto.api.UsersServiceKt.setStudentEnrollmentSelectionRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(Transactional::class)
class UsersRoutesTest : ApplicationTest() {
    private val ApplicationTestBuilder.usersService: UsersService
        get() {
            val service: UsersService by application.dependencies
            return service
        }

    private suspend fun ApplicationTestBuilder.studentToken(): String {
        startApplication()
        return usersService.createSession(
            STUDENT_ID,
            PASSWORD,
            ""
        )
    }

    private suspend fun ApplicationTestBuilder.teacherToken(): String {
        startApplication()
        return usersService.createSession(
            TEACHER_ID,
            PASSWORD,
            ""
        )
    }

    @Test
    fun `get student user`() = runRouteTest {
        val user = client.getWithAuth("/users/${STUDENT_ID}", studentToken())
            .assertOK()
            .parse<User>()

        assertEquals(STUDENT_ID, user.id)
        assertEquals(UserType.STUDENT, user.type)
    }

    @Test
    fun `get teacher user`() = runRouteTest {
        val user = client.getWithAuth("/users/${TEACHER_ID}", teacherToken())
            .assertOK()
            .parse<User>()

        assertEquals(TEACHER_ID, user.id)
        assertEquals(UserType.TEACHER, user.type)
    }

    @Test
    fun `get user with me alias`() = runRouteTest {
        val user = client.getWithAuth("/users/@me", studentToken())
            .assertOK()
            .parse<User>()

        assertEquals(STUDENT_ID, user.id)
        assertEquals(UserType.STUDENT, user.type)
    }

    @Test
    fun `get user not found`() = runRouteTest {
        client.getWithAuth("/users/${UNUSED_ID}", teacherToken())
            .assertNotFound()
    }

    @Test
    fun `get user invalid id format`() = runRouteTest {
        client.getWithAuth("/users/notanumber", teacherToken())
            .assertBadRequest()
    }

    @Test
    fun `get other student as student`() = runRouteTest {
        client.getWithAuth("/users/${TEACHER_ID}", studentToken())
            .assertUnauthorized()
    }

    @Test
    fun `get student selections`() = runRouteTest {
        val selections = client.getWithAuth("/users/@me/selections", studentToken())
            .assertOK()
            .parse<th.ac.bodin2.electives.proto.api.UsersService.StudentSelections>()

        assertEquals(SUBJECT_ID, selections.subjectsMap[ENROLLMENT_ID]?.id)
        assertEquals(TEACHER_ID, selections.subjectsMap[ENROLLMENT_ID]?.teachersList?.first()?.id)
    }

    @Test
    fun `get selections for non student`() = runRouteTest {
        client.getWithAuth("/users/@me/selections", teacherToken())
            .assertBadRequest()
    }

    private suspend fun ApplicationTestBuilder.modifySelectionWithResult(
        serviceResponse: ModifySelectionResult,
        delete: Boolean = false
    ): HttpResponse {
        testEnrollmentSelectionServiceResponse = serviceResponse

        val url = "/users/@me/selections/${ENROLLMENT_ID}"
        val token = studentToken()

        if (delete) {
            return client.deleteWithAuth(url, token)
        }

        return client.putProtoWithAuth(
            url,
            setStudentEnrollmentSelectionRequest {
                subjectId = SUBJECT_ID
            },
            token
        )
    }

    @Test
    fun `modify selection success`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.Success).assertOK()
    }

    @Test
    fun `modify selection on not found enrollment`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.NotFound(ExceptionEntity.ENROLLMENT))
            .assertNotFound("Enrollment not found")
    }

    @Test
    fun `modify selection on not found subject`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.NotFound(ExceptionEntity.SUBJECT))
            .assertBadRequest("Subject does not exist")
    }

    @Test
    fun `modify selection on non-student user`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.NotFound(ExceptionEntity.STUDENT))
            .assertBadRequest("Modifying selections for non-student users")
    }

    @Test
    fun `modify selection forbidden`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.CannotModify(ModifySelectionStatus.FORBIDDEN))
            .assertForbidden("Not allowed")
    }

    @Test
    fun `modify selection not enrolled`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.CannotModify(ModifySelectionStatus.NOT_ENROLLED), true)
            .assertBadRequest("Student has not enrolled in the selected enrollment")
    }

    @Test
    fun `modify selection subject not in enrollment`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.CannotEnroll(CanEnrollStatus.SUBJECT_NOT_IN_ENROLLMENT))
            .assertBadRequest("Subject is not part of this enrollment")
    }

    @Test
    fun `modify selection already enrolled`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.CannotEnroll(CanEnrollStatus.ALREADY_ENROLLED))
            .assertConflict("Student has already enrolled for this enrollment")
    }

    @Test
    fun `modify selection not in enrollment group`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.CannotEnroll(CanEnrollStatus.NOT_IN_ENROLLMENT_GROUP))
            .assertForbidden("Student does not pass the group requirements")
    }

    @Test
    fun `modify selection not in subject group`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.CannotEnroll(CanEnrollStatus.NOT_IN_SUBJECT_GROUP))
            .assertForbidden("Student does not pass the group requirements")
    }

    @Test
    fun `modify selection subject full`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.CannotEnroll(CanEnrollStatus.SUBJECT_FULL))
            .assertBadRequest("Selected subject is full")
    }

    @Test
    fun `modify selection not in enrollment date range`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.CannotEnroll(CanEnrollStatus.NOT_IN_ENROLLMENT_DATE_RANGE))
            .assertBadRequest("Not in enrollment date range")
    }

    @Test
    fun `delete selection not in enrollment date range`() = runRouteTest {
        modifySelectionWithResult(
            ModifySelectionResult.CannotEnroll(CanEnrollStatus.NOT_IN_ENROLLMENT_DATE_RANGE),
            true
        )
            .assertBadRequest("Not in enrollment date range")
    }

    @Test
    fun `get teacher subjects`() = runRouteTest {
        val response = client.getWithAuth("/users/@me/subjects", teacherToken())
            .assertOK()
            .parse<th.ac.bodin2.electives.proto.api.UsersService.TeacherSubjects>()

        assertTrue(response.subjectsMap.isNotEmpty())
        assertEquals(SUBJECT_ID, response.subjectsMap.values.first().id)
    }

    @Test
    fun `get subjects for non-teacher`() = runRouteTest {
        client.getWithAuth("/users/@me/subjects", studentToken())
            .assertBadRequest("Viewing subjects for non-teacher users")
    }

    @Test
    fun `get other user subjects as teacher`() = runRouteTest {
        client.getWithAuth("/users/$STUDENT_ID/subjects", teacherToken())
            .assertBadRequest("Viewing subjects for non-teacher users")
    }
}
