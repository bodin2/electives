package th.ac.bodin2.electives.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.api.TestConstants.TestData
import th.ac.bodin2.electives.api.services.TestServiceConstants
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.proto.api.AuthService
import th.ac.bodin2.electives.proto.api.AuthServiceKt
import th.ac.bodin2.electives.proto.api.AuthServiceKt.authenticateRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthRoutesTest : ApplicationTest() {
    private suspend fun HttpClient.auth(builder: AuthServiceKt.AuthenticateRequestKt.Dsl.() -> Unit) =
        this.postProto("/auth", authenticateRequest(builder))

    @Test
    fun `authenticate success`() = runRouteTest {
        val response = client.auth {
            id = TestServiceConstants.STUDENT_ID
            password = TestServiceConstants.PASSWORD
            clientName = TestData.CLIENT_NAME
        }.assertOK().parse<AuthService.AuthenticateResponse>()

        assertTrue(response.token.isNotBlank())
    }

    @Test
    fun `authenticate invalid password`() = runRouteTest {
        val response = client.auth {
            id = TestServiceConstants.STUDENT_ID
            password = ""
            clientName = TestData.CLIENT_NAME
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `authenticate user not found`() = runRouteTest {
        val response = client.auth {
            id = 0
            password = ""
            clientName = TestData.CLIENT_NAME
        }

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

        val token = transaction {
            usersService.createSession(
                TestServiceConstants.STUDENT_ID,
                TestServiceConstants.PASSWORD,
                TestData.CLIENT_NAME
            )
        }

        client.post("/logout") {
            bearerAuth(token)
        }.assertOK()

        assertFailsWith<IllegalArgumentException> {
            transaction { usersService.getSessionUserId(token) }
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