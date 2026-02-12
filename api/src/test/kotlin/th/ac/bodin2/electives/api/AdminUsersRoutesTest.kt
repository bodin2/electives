package th.ac.bodin2.electives.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.mockk.every
import th.ac.bodin2.electives.api.services.AdminAuthService
import th.ac.bodin2.electives.api.services.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.TEACHER_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.proto.api.AdminService
import th.ac.bodin2.electives.proto.api.AdminServiceKt.addUserRequest
import th.ac.bodin2.electives.proto.api.User
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.proto.api.user
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminUsersRoutesTest : ApplicationTest() {
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

    private suspend fun HttpClient.adminDelete(url: String): HttpResponse =
        deleteWithAuth(url, "admin-token")

    @Test
    fun `get user without auth returns unauthorized`() = runRouteTest {
        client.get("/admin/users/$STUDENT_ID").assertUnauthorized()
    }

    @Test
    fun `get student user`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        val user = client.adminGet("/admin/users/$STUDENT_ID")
            .assertOK()
            .parse<User>()

        assertEquals(STUDENT_ID, user.id)
    }

    @Test
    fun `get teacher user`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        val user = client.adminGet("/admin/users/$TEACHER_ID")
            .assertOK()
            .parse<User>()

        assertEquals(TEACHER_ID, user.id)
    }

    @Test
    fun `get user not found`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        client.adminGet("/admin/users/$UNUSED_ID").assertNotFound()
    }

    @Test
    fun `get students list`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        val response = client.adminGet("/admin/users/students")
            .assertOK()
            .parse<AdminService.ListUsersResponse>()

        assertTrue(response.usersCount >= 0)
    }

    @Test
    fun `get teachers list`() = runRouteTest {
        startApplication()
        enableAdminAuth()

        val response = client.adminGet("/admin/users/teachers")
            .assertOK()
            .parse<AdminService.ListUsersResponse>()

        assertTrue(response.usersCount >= 0)
    }

    @Test
    fun `delete user without auth returns unauthorized`() = runRouteTest {
        client.delete("/admin/users/$STUDENT_ID").assertUnauthorized()
    }
}
