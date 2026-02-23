package th.ac.bodin2.electives.api

import io.ktor.client.plugins.websocket.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.api.TestDatabase.mockData
import th.ac.bodin2.electives.api.services.*
import th.ac.bodin2.electives.api.services.mock.*
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

typealias TransactionBlock = JdbcTransaction.() -> Any?

abstract class ApplicationTest {
    private fun mockTransactions() {
        mockkStatic("org.jetbrains.exposed.v1.jdbc.transactions.TransactionsKt")
        every {
            transaction<Any?>(any(), any(), any(), any())
        } answers {
            lastArg<TransactionBlock>().invoke(mockk(relaxed = true))
        }
    }

    private fun unmockTransactions() {
        unmockkStatic("org.jetbrains.exposed.v1.jdbc.transactions.TransactionsKt")
    }

    fun Application.mockRateLimits() {
        install(RateLimit) {
            val mock: RateLimitProviderConfig.() -> Unit = {
                rateLimiter(Int.MAX_VALUE, 0.seconds)
            }

            register(RATE_LIMIT_ADMIN, mock)
            register(RATE_LIMIT_ADMIN_AUTH, mock)
            register(RATE_LIMIT_AUTH, mock)
            register(RATE_LIMIT_ELECTIVES, mock)
            register(RATE_LIMIT_ELECTIVES_SUBJECT_MEMBERS, mock)
            register(RATE_LIMIT_NOTIFICATIONS, mock)
            register(RATE_LIMIT_USERS, mock)
            register(RATE_LIMIT_USERS_SELECTIONS, mock)
        }
    }

    open fun runRouteTest(block: suspend ApplicationTestBuilder.() -> Unit) {
        setupTestEnvironment()
        mockTransactions()
        MockUtils.mockDAOHelpers()

        testApplication {
            application {
                dependencies {
                    provide<UsersService> { TestUsersService() }
                    provide<NotificationsService> { mockk(relaxed = true) }
                    provide<ElectiveService> { TestElectiveService() }
                    provide<ElectiveSelectionService> { TestElectiveSelectionService() }
                    provide<SubjectService> { TestSubjectService() }
                    provide<TeamService> { TestTeamService() }
                    provide<AdminAuthService> { mockk(relaxed = true) }
                }

                mockRateLimits()
                module()
            }

            block()
        }

        unmockTransactions()
        MockUtils.unmockDAOHelpers()
    }

    open fun runTest(setup: TestApplicationBuilder.() -> Unit, block: suspend ApplicationTestBuilder.() -> Unit) {
        setupTestEnvironment()

        testApplication {
            setup()

            application {
                provideDependencies()
                mockRateLimits()
                module()
            }

            TestDatabase.connect()
            println("Connected to test database.")
            transaction { TestDatabase.reset() }

            startApplication()

            transaction { application.mockData() }

            block()
        }
    }

    open fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) {
        runTest({}, block)
    }

    @Suppress("NOTHING_TO_INLINE")
    private suspend inline fun HttpResponse.assertStatus(
        expectedStatus: HttpStatusCode,
        msg: String? = null
    ) = apply {
        assertEquals(expectedStatus, status)
        msg?.let { assertEquals(it, bodyAsText()) }
    }

    suspend fun HttpResponse.assertOK(msg: String? = null) = assertStatus(HttpStatusCode.OK, msg)
    suspend fun HttpResponse.assertNotFound(msg: String? = null) = assertStatus(HttpStatusCode.NotFound, msg)
    suspend fun HttpResponse.assertBadRequest(msg: String? = null) = assertStatus(HttpStatusCode.BadRequest, msg)
    suspend fun HttpResponse.assertUnauthorized(msg: String? = null) = assertStatus(HttpStatusCode.Unauthorized, msg)
    suspend fun HttpResponse.assertForbidden(msg: String? = null) = assertStatus(HttpStatusCode.Forbidden, msg)
    suspend fun HttpResponse.assertConflict(msg: String? = null) = assertStatus(HttpStatusCode.Conflict, msg)

    fun ApplicationTestBuilder.createWSClient() = this@createWSClient.createClient {
        install(WebSockets) {
            pingInterval = 15.seconds
        }
    }
}