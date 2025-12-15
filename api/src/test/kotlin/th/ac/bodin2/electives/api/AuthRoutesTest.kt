package th.ac.bodin2.electives.api

import io.ktor.client.request.*
import io.ktor.http.*
import org.jetbrains.exposed.sql.transactions.transaction
import th.ac.bodin2.electives.api.TestConstants.Lengths
import th.ac.bodin2.electives.api.TestConstants.Students
import th.ac.bodin2.electives.api.TestConstants.TestData
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.proto.api.AuthService
import th.ac.bodin2.electives.proto.api.AuthServiceKt
import kotlin.test.*

class AuthRoutesTest : ApplicationTest() {
    @Test
    fun `authenticate success`() = runTest {
        val response = client.postProto("/auth", AuthServiceKt.authenticateRequest {
            id = Students.JOHN_ID
            password = Students.JOHN_PASSWORD
            clientName = TestData.CLIENT_NAME
        })

        assertEquals(HttpStatusCode.OK, response.status)
        val authResponse = response.parseProto<AuthService.AuthenticateResponse>()
        assertTrue(authResponse.token.isNotBlank())
    }

    @Test
    fun `authenticate invalid password`() = runTest {
        val response = client.postProto("/auth", AuthServiceKt.authenticateRequest {
            id = Students.JOHN_ID
            password = "wrongpassword"
            clientName = TestData.CLIENT_NAME
        })

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `authenticate user not found`() = runTest {
        val response = client.postProto("/auth", AuthServiceKt.authenticateRequest {
            id = TestData.NONEXISTENT_ID
            password = Students.JOHN_PASSWORD
            clientName = TestData.CLIENT_NAME
        })

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `authenticate invalid request`() = runTest {
        val response = client.post("/auth") {
            contentType(ContentType.Application.ProtoBuf)
            setBody(byteArrayOf(1, 2, 3))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `logout success`() = runTest {
        val token = transaction {
            UsersService.createSession(
                Students.JOHN_ID,
                Students.JOHN_PASSWORD,
                TestData.CLIENT_NAME
            )
        }

        val response = client.post("/logout") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        assertFailsWith<IllegalArgumentException> {
            transaction { UsersService.getSessionUserId(token) }
        }
    }

    @Test
    fun `logout unauthorized`() = runTest {
        val response = client.post("/logout")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `logout invalid token`() = runTest {
        val response = client.post("/logout") {
            bearerAuth(TestData.INVALID_TOKEN)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `authenticate with empty client name`() = runTest {
        val response = client.postProto("/auth", AuthServiceKt.authenticateRequest {
            id = Students.JOHN_ID
            password = Students.JOHN_PASSWORD
            clientName = ""
        })

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `authenticate with password too long`() = runTest {
        val response = client.postProto("/auth", AuthServiceKt.authenticateRequest {
            id = Students.JOHN_ID
            password = "p".repeat(Lengths.AUTH_PASSWORD_TOO_LONG)
            clientName = TestData.CLIENT_NAME
        })

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `authenticate with client name too long`() = runTest {
        val response = client.postProto("/auth", AuthServiceKt.authenticateRequest {
            id = Students.JOHN_ID
            password = Students.JOHN_PASSWORD
            clientName = "c".repeat(Lengths.AUTH_CLIENTNAME_TOO_LONG)
        })

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `authenticate multiple times generates different tokens`() = runTest {
        val response1 = client.postProto("/auth", AuthServiceKt.authenticateRequest {
            id = Students.JOHN_ID
            password = Students.JOHN_PASSWORD
            clientName = TestData.CLIENT_NAME
        })

        val response2 = client.postProto("/auth", AuthServiceKt.authenticateRequest {
            id = Students.JOHN_ID
            password = Students.JOHN_PASSWORD
            clientName = TestData.CLIENT_NAME
        })

        assertEquals(HttpStatusCode.OK, response1.status)
        assertEquals(HttpStatusCode.OK, response2.status)

        val token1 = response1.parseProto<AuthService.AuthenticateResponse>().token
        val token2 = response2.parseProto<AuthService.AuthenticateResponse>().token

        assertNotEquals(token1, token2)
    }

    @Test
    fun `logout and reauthenticate`() = runTest {
        val token1 = transaction {
            UsersService.createSession(
                Students.JOHN_ID,
                Students.JOHN_PASSWORD,
                TestData.CLIENT_NAME
            )
        }

        val logoutResponse = client.post("/logout") {
            bearerAuth(token1)
        }

        assertEquals(HttpStatusCode.OK, logoutResponse.status)

        val authResponse = client.postProto("/auth", AuthServiceKt.authenticateRequest {
            id = Students.JOHN_ID
            password = Students.JOHN_PASSWORD
            clientName = TestData.CLIENT_NAME
        })

        assertEquals(HttpStatusCode.OK, authResponse.status)
    }

    @Test
    fun `logout twice`() = runTest {
        val token = transaction {
            UsersService.createSession(
                Students.JOHN_ID,
                Students.JOHN_PASSWORD,
                TestData.CLIENT_NAME
            )
        }

        val response1 = client.post("/logout") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response1.status)

        val response2 = client.post("/logout") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.Unauthorized, response2.status)
    }
}