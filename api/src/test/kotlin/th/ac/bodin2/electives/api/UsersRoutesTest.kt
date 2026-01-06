package th.ac.bodin2.electives.api

import io.ktor.client.statement.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.api.services.ElectiveSelectionService.*
import th.ac.bodin2.electives.api.services.TestServiceConstants.ELECTIVE_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.PASSWORD
import th.ac.bodin2.electives.api.services.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.TEACHER_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.services.testElectiveSelectionServiceResponse
import th.ac.bodin2.electives.proto.api.User
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.proto.api.UsersServiceKt.setStudentElectiveSelectionRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class UsersRoutesTest : ApplicationTest() {
    private val ApplicationTestBuilder.usersService: UsersService
        get() {
            val service: UsersService by application.dependencies
            return service
        }

    private suspend fun ApplicationTestBuilder.studentToken(): String {
        startApplication()
        return transaction {
            usersService.createSession(
                STUDENT_ID,
                PASSWORD,
                ""
            )
        }
    }

    private suspend fun ApplicationTestBuilder.teacherToken(): String {
        startApplication()
        return transaction {
            usersService.createSession(
                TEACHER_ID,
                PASSWORD,
                ""
            )
        }
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
            .parse<th.ac.bodin2.electives.proto.api.UsersService.GetStudentSelectionsResponse>()

        assertEquals(SUBJECT_ID, selections.subjectsMap[ELECTIVE_ID]?.id)
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
        testElectiveSelectionServiceResponse = serviceResponse

        val url = "/users/@me/selections/${ELECTIVE_ID}"
        val token = studentToken()

        if (delete) {
            return client.deleteWithAuth(url, token)
        }

        return client.putProtoWithAuth(
            url,
            setStudentElectiveSelectionRequest {
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
    fun `modify selection on not found elective`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.NotFound(NotFoundEntity.ELECTIVE))
            .assertNotFound("Elective not found")
    }

    @Test
    fun `modify selection on not found subject`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.NotFound(NotFoundEntity.SUBJECT))
            .assertBadRequest("Subject does not exist")
    }

    @Test
    fun `modify selection on non-student user`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.NotFound(NotFoundEntity.STUDENT))
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
            .assertBadRequest("Student has not enrolled in the selected elective")
    }

    @Test
    fun `modify selection subject not in elective`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.CannotEnroll(CanEnrollStatus.SUBJECT_NOT_IN_ELECTIVE))
            .assertBadRequest("Subject is not part of this elective")
    }

    @Test
    fun `modify selection already enrolled`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.CannotEnroll(CanEnrollStatus.ALREADY_ENROLLED))
            .assertConflict("Student has already enrolled for this elective")
    }

    @Test
    fun `modify selection not in elective team`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.CannotEnroll(CanEnrollStatus.NOT_IN_ELECTIVE_TEAM))
            .assertForbidden("Student does not pass the team requirements")
    }

    @Test
    fun `modify selection not in subject team`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.CannotEnroll(CanEnrollStatus.NOT_IN_SUBJECT_TEAM))
            .assertForbidden("Student does not pass the team requirements")
    }

    @Test
    fun `modify selection subject full`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.CannotEnroll(CanEnrollStatus.SUBJECT_FULL))
            .assertBadRequest("Selected subject is full")
    }

    @Test
    fun `modify selection not in elective date range`() = runRouteTest {
        modifySelectionWithResult(ModifySelectionResult.CannotEnroll(CanEnrollStatus.NOT_IN_ELECTIVE_DATE_RANGE))
            .assertBadRequest("Not in elective enrollment date range")
    }
}
