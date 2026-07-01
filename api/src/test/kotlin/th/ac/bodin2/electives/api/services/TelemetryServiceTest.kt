package th.ac.bodin2.electives.api.services

import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.testing.*
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import kotlinx.coroutines.runBlocking
import th.ac.bodin2.electives.api.setupTestEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TelemetryServiceTest {
    @BeforeTest
    fun setup() {
        setupTestEnvironment()
        Telemetry.tracer = null
    }

    @AfterTest
    fun teardown() {
        Telemetry.tracer = null
    }

    @Test
    fun `disabled service exposes no tracer`() {
        val telemetry = TelemetryService(disabled = true)
        assertNull(telemetry.tracer, "disabled telemetry must not expose a tracer")
    }

    @Test
    fun `disabled configure installs no plugins and sets no global tracer`() = testApplication {
        application {
            val telemetry = TelemetryService(disabled = true)
            telemetry.configure(this)

            assertNull(pluginOrNull(MicrometerMetrics), "MicrometerMetrics must not be installed when disabled")
            assertNull(pluginOrNull(KtorServerTelemetry), "KtorServerTelemetry must not be installed when disabled")
            assertNull(Telemetry.tracer, "the global tracer must stay null when telemetry is disabled")
        }

        startApplication()
    }

    @Test
    fun `withSpan runs the block and returns its result when disabled`() = runBlocking {
        val telemetry = TelemetryService(disabled = true)
        val result = telemetry.withSpan("noop") { 42 }
        assertEquals(42, result)
    }

    @Test
    fun `close is safe to call when disabled`() {
        val telemetry = TelemetryService(disabled = true)
        // Should not throw
        telemetry.close()
        assertNull(Telemetry.tracer)
    }
}
