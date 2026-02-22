package th.ac.bodin2.electives.api.routes

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.every
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.parse
import th.ac.bodin2.electives.api.postProto
import th.ac.bodin2.electives.api.services.AdminAuthService
import th.ac.bodin2.electives.api.services.AdminAuthService.CreateSessionResult
import th.ac.bodin2.electives.proto.api.AdminService
import th.ac.bodin2.electives.proto.api.AuthService
import th.ac.bodin2.electives.proto.api.AuthServiceKt.authenticateRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminAuthRoutesTest : ApplicationTest() {
    private val ApplicationTestBuilder.adminAuthService: AdminAuthService
        get() {
            val service: AdminAuthService by application.dependencies
            return service
        }

    @Test
    fun `get challenge`() = runRouteTest {
        startApplication()
        every { adminAuthService.newChallenge() } returns "test-challenge"

        val response = client.get("/admin/challenge")
            .assertOK()
            .parse<AdminService.ChallengeResponse>()

        assertEquals(response.challenge, "test-challenge")
    }

    @Test
    fun `authenticate success`() = runRouteTest {
        startApplication()
        coEvery { adminAuthService.createSession(any(), any()) } returns
                CreateSessionResult.Success("admin-token-123")

        val response = client.postProto(
            "/admin/auth",
            authenticateRequest {
                password = "signed-challenge"
            }
        ).assertOK().parse<AuthService.AuthenticateResponse>()

        assertEquals(response.token, "admin-token-123")
    }

    @Test
    fun `authenticate no challenge`() = runRouteTest {
        startApplication()
        coEvery { adminAuthService.createSession(any(), any()) } returns
                CreateSessionResult.NoChallenge

        client.postProto(
            "/admin/auth",
            authenticateRequest {
                password = "some-signature"
            }
        ).assertNotFound()
    }

    @Test
    fun `authenticate invalid signature`() = runRouteTest {
        startApplication()
        coEvery { adminAuthService.createSession(any(), any()) } returns
                CreateSessionResult.InvalidSignature

        val response = client.postProto(
            "/admin/auth",
            authenticateRequest {
                password = "bad-signature"
            }
        )

        response.assertUnauthorized()
    }

    @Test
    fun `authenticate ip not allowed`() = runRouteTest {
        startApplication()
        coEvery { adminAuthService.createSession(any(), any()) } returns
                CreateSessionResult.IPNotAllowed("192.168.1.1")

        client.postProto(
            "/admin/auth",
            authenticateRequest {
                password = "signed-challenge"
            }
        ).assertNotFound()
    }

    @Test
    fun `authenticate invalid request body`() = runRouteTest {
        val response = client.post("/admin/auth") {
            contentType(ContentType.Application.ProtoBuf)
            setBody(byteArrayOf(1, 2, 3))
        }

        response.assertNotFound()
    }

    @Test
    fun `authenticate exception returns unauthorized`() = runRouteTest {
        startApplication()
        coEvery { adminAuthService.createSession(any(), any()) } throws
                RuntimeException("Something went wrong")

        val response = client.postProto(
            "/admin/auth",
            authenticateRequest {
                password = "signed-challenge"
            }
        )

        response.assertUnauthorized()
    }
}
