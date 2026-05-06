package th.ac.bodin2.electives.api.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.TestConstants.TestData
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.parse
import th.ac.bodin2.electives.api.postProto
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants
import th.ac.bodin2.electives.proto.api.AuthService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthRoutesTest : ApplicationTest() {
    private suspend fun HttpClient.auth(request: AuthService.AuthenticateRequest) =
        this.postProto("/auth", request)

    @Test
    fun `authenticate success`() = runRouteTest {
        val response = client.auth(
            AuthService.AuthenticateRequest(
                id = TestServiceConstants.STUDENT_ID.toInt(),
                password = TestServiceConstants.PASSWORD,
                client_name = TestData.CLIENT_NAME
            )
        ).assertOK().parse<AuthService.AuthenticateResponse>()

        assertTrue(response.token.isNotBlank())
    }

    @Test
    fun `authenticate invalid password`() = runRouteTest {
        val response = client.auth(
            AuthService.AuthenticateRequest(
                id = TestServiceConstants.STUDENT_ID.toInt(),
                password = "",
                client_name = TestData.CLIENT_NAME
            )
        )

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `authenticate user not found`() = runRouteTest {
        val response = client.auth(
            AuthService.AuthenticateRequest(
                id = 0,
                password = "",
                client_name = TestData.CLIENT_NAME
            )
        )

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `authenticate invalid request`() = runRouteTest {
        val response = client.post("/auth") {
            contentType(ContentType.Application.ProtoBuf)
            setBody(byteArrayOf(1, 2, 3))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `logout success`() = runRouteTest {
        startApplication()

        val usersService: UsersService by application.dependencies

        @OptIn(Transactional::class)
        val token = usersService.createSession(
            TestServiceConstants.STUDENT_ID,
            TestServiceConstants.PASSWORD,
            TestData.CLIENT_NAME
        )

        client.post("/logout") {
            bearerAuth(token)
        }.assertOK()

        assertFailsWith<IllegalArgumentException> {
            transaction { usersService.getSessionUser(token) }
        }
    }

    @Test
    fun `logout with bad token`() = runRouteTest {
        val response = client.post("/logout") {
            bearerAuth("")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
