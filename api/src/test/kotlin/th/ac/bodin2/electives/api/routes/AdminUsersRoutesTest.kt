package th.ac.bodin2.electives.api.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.deleteWithAuth
import th.ac.bodin2.electives.api.getWithAuth
import th.ac.bodin2.electives.api.parse
import th.ac.bodin2.electives.api.patchProtoWithAuth
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ADMIN_TOKEN
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.TEACHER_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.proto.api.AdminService
import th.ac.bodin2.electives.proto.api.AdminServiceKt.userPatch
import th.ac.bodin2.electives.proto.api.User
import th.ac.bodin2.electives.proto.api.UserType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminUsersRoutesTest : ApplicationTest() {
    private suspend fun HttpClient.adminGet(url: String): HttpResponse =
        getWithAuth(url, ADMIN_TOKEN)

    private suspend fun HttpClient.adminDelete(url: String): HttpResponse =
        deleteWithAuth(url, ADMIN_TOKEN)

    @Test
    fun `get user not found`() = runRouteTest {
        startApplication()

        client.adminGet("/admin/users/$UNUSED_ID").assertNotFound()
    }

    @Test
    fun `get students list`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/users/students")
            .assertOK()
            .parse<AdminService.ListUsersResponse>()

        assertTrue(response.usersCount >= 0)
    }

    @Test
    fun `get teachers list`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/users/teachers")
            .assertOK()
            .parse<AdminService.ListUsersResponse>()

        assertTrue(response.usersCount >= 0)
    }

    @Test
    fun `get students list with query`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/users/students?query=test")
            .assertOK()
            .parse<AdminService.ListUsersResponse>()

        assertTrue(response.usersCount >= 0)
    }

    @Test
    fun `get teachers list with query`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/users/teachers?query=test")
            .assertOK()
            .parse<AdminService.ListUsersResponse>()

        assertTrue(response.usersCount >= 0)
    }

    @Test
    fun `delete user without auth returns unauthorized`() = runRouteTest {
        client.delete("/admin/users/$STUDENT_ID").assertUnauthorized()
    }

    @Test
    fun `patch user without auth returns unauthorized`() = runRouteTest {
        client.patch("/admin/users/$STUDENT_ID").assertUnauthorized()
    }

    @Test
    fun `patch student returns updated user`() = runRouteTest {
        startApplication()

        val user = client.patchProtoWithAuth(
            "/admin/users/$STUDENT_ID",
            userPatch { firstName = "New Name" },
            ADMIN_TOKEN
        ).assertOK().parse<User>()

        assertEquals(STUDENT_ID, user.id)
        assertEquals(UserType.STUDENT, user.type)
    }

    @Test
    fun `patch teacher returns updated user`() = runRouteTest {
        startApplication()

        val user = client.patchProtoWithAuth(
            "/admin/users/$TEACHER_ID",
            userPatch { firstName = "New Name" },
            ADMIN_TOKEN
        ).assertOK().parse<User>()

        assertEquals(TEACHER_ID, user.id)
        assertEquals(UserType.TEACHER, user.type)
    }

    @Test
    fun `patch user not found`() = runRouteTest {
        startApplication()

        client.patchProtoWithAuth(
            "/admin/users/$UNUSED_ID",
            userPatch { firstName = "New Name" },
            ADMIN_TOKEN
        ).assertNotFound("User not found")
    }

    @Test
    fun `patch student with prefix sets prefix in response`() = runRouteTest {
        startApplication()

        val user = client.patchProtoWithAuth(
            "/admin/users/$STUDENT_ID",
            userPatch {
                prefix = "Dr."
                patchPrefix = true
            },
            ADMIN_TOKEN
        ).assertOK().parse<User>()

        assertEquals(STUDENT_ID, user.id)
        assertEquals(UserType.STUDENT, user.type)
    }

    @Test
    fun `patch teacher with prefix sets prefix in response`() = runRouteTest {
        startApplication()

        val user = client.patchProtoWithAuth(
            "/admin/users/$TEACHER_ID",
            userPatch {
                prefix = "Prof."
                patchPrefix = true
            },
            ADMIN_TOKEN
        ).assertOK().parse<User>()

        assertEquals(TEACHER_ID, user.id)
        assertEquals(UserType.TEACHER, user.type)
    }

    @Test
    fun `patch student without patchPrefix does not require prefix field`() = runRouteTest {
        startApplication()

        val user = client.patchProtoWithAuth(
            "/admin/users/$STUDENT_ID",
            userPatch {
                firstName = "Updated"
                // patchPrefix = false (default), prefix not set
            },
            ADMIN_TOKEN
        ).assertOK().parse<User>()

        assertEquals(STUDENT_ID, user.id)
    }
}
