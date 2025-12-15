package th.ac.bodin2.electives.api

import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration.Companion.seconds

abstract class ApplicationTest {
    fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) {
        setupTestEnvironment()

        testApplication {
            if (!TransactionManager.isInitialized()) {
                TestDatabase.connect()
                println("Connected to test database.")
            }

            transaction {
                TestDatabase.reset()
                TestDatabase.mockData()
            }

            application { module() }

            block()
        }
    }

    fun ApplicationTestBuilder.createWSClient() = this@createWSClient.createClient {
        install(WebSockets) {
            pingInterval = 15.seconds
        }
    }
}