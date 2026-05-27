package th.ac.bodin2.electives.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.utils.badRequest
import th.ac.bodin2.electives.api.utils.conflict
import th.ac.bodin2.electives.api.utils.forbidden
import th.ac.bodin2.electives.api.utils.notFound
import th.ac.bodin2.electives.api.utils.unauthorized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusPagesTest {
    private fun runStatusPagesTest(block: suspend ApplicationTestBuilder.() -> Unit) {
        setupTestEnvironment()
        testApplication {
            application {
                configureStatusPages()
                routing {
                    get("/client/badRequest") { throw badRequest("Bad thing happened") }
                    get("/client/notFound") { throw notFound("Missing thing") }
                    get("/client/unauthorized") { throw unauthorized("No token") }
                    get("/client/forbidden") { throw forbidden("No access") }
                    get("/client/conflict") { throw conflict("Already there") }
                    get("/client/nullMessage") { throw badRequest(null) }
                    get("/entity/notFound") { throw EntityNotFoundException(ExceptionEntity.USER) }
                    get("/entity/conflict") { throw ConflictException(ExceptionEntity.SUBJECT) }
                    get("/entity/nothingToUpdate") { throw NothingToUpdateException() }
                    get("/fatal") { throw RuntimeException("secret internal detail") }
                }
            }
            block()
        }
    }

    @Test
    fun `throw badRequest helper returns 400 with message`() = runStatusPagesTest {
        val response = client.get("/client/badRequest")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Bad thing happened", response.bodyAsText())
    }

    @Test
    fun `throw notFound helper returns 404 with message`() = runStatusPagesTest {
        val response = client.get("/client/notFound")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("Missing thing", response.bodyAsText())
    }

    @Test
    fun `throw unauthorized helper returns 401 with message`() = runStatusPagesTest {
        val response = client.get("/client/unauthorized")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("No token", response.bodyAsText())
    }

    @Test
    fun `throw forbidden helper returns 403 with message`() = runStatusPagesTest {
        val response = client.get("/client/forbidden")
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("No access", response.bodyAsText())
    }

    @Test
    fun `throw conflict helper returns 409 with message`() = runStatusPagesTest {
        val response = client.get("/client/conflict")
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("Already there", response.bodyAsText())
    }

    @Test
    fun `ClientException with null message returns status only`() = runStatusPagesTest {
        val response = client.get("/client/nullMessage")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `EntityNotFoundException returns 404 with entity display name`() = runStatusPagesTest {
        val response = client.get("/entity/notFound")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("User not found", response.bodyAsText())
    }

    @Test
    fun `ConflictException returns 409 with entity display name`() = runStatusPagesTest {
        val response = client.get("/entity/conflict")
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("Subject already exists", response.bodyAsText())
    }

    @Test
    fun `NothingToUpdateException returns 400`() = runStatusPagesTest {
        val response = client.get("/entity/nothingToUpdate")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Nothing to update", response.bodyAsText())
    }

    @Test
    fun `unhandled exception returns 500 without internal details in production mode`() = runStatusPagesTest {
        val response = client.get("/fatal")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val body = response.bodyAsText()
        assertEquals("Internal Server Error", body)
        assertTrue("secret internal detail" !in body, "Internal detail must not leak in response body")
        assertTrue("RuntimeException" !in body, "Exception type must not leak in response body")

        val traceId = response.headers["X-Trace-Id"]
        assertNotNull(traceId, "X-Trace-Id header must be present in production mode")
        val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        assertTrue(uuidRegex.matches(traceId), "X-Trace-Id must be a valid UUID, got: $traceId")
    }

    @Test
    fun `unhandled exception in dev mode exposes stack trace and no trace header`() {
        System.setProperty("APP_ENV", "development")
        try {
            testApplication {
                application {
                    configureStatusPages()
                    routing {
                        get("/fatal") { throw RuntimeException("dev visible detail") }
                    }
                }

                val response = client.get("/fatal")
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                val body = response.bodyAsText()
                assertTrue("dev visible detail" in body, "Dev mode must expose exception details")
                assertTrue("RuntimeException" in body, "Dev mode must expose exception type")
                assertNull(response.headers["X-Trace-Id"], "X-Trace-Id must NOT be set in dev mode")
            }
        } finally {
            System.setProperty("APP_ENV", "test")
        }
    }
}
