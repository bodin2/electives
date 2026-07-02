package th.ac.bodin2.electives.api.services

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.registry.otlp.OtlpConfig
import io.micrometer.registry.otlp.OtlpMeterRegistry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.utils.env
import java.time.Duration

/**
 * Process-wide tracer, so non-DI parts of the code can get the instance.
 *
 * When telemetry is disabled the [tracer] stays `null`.
 */
object Telemetry {
    @Volatile
    var tracer: Tracer? = null
        internal set
}

/**
 * Initializes the OpenTelemetry SDK (traces + logs) and the Micrometer OTLP registry (metrics), and installs related Ktor plugins.
 *
 * When constructed with [disabled] set to `true`, every method is a no-op and no SDK, exporter, or registry is created.
 */
class TelemetryService(
    private val disabled: Boolean,
    private val config: Config = Config.fromEnv(),
) {
    class Config(
        val serviceName: String,
        val otlpEndpoint: String,
        val otlpHeaders: Map<String, String>,
        val metricStep: Duration,
    ) {
        companion object {
            fun fromEnv() = Config(
                serviceName = env("OTEL_SERVICE_NAME") ?: "electives-api",
                otlpEndpoint = (env("OTEL_EXPORTER_OTLP_ENDPOINT") ?: "").trimEnd('/'),
                otlpHeaders = parseHeaders(env("OTEL_EXPORTER_OTLP_HEADERS")),
                metricStep = Duration.ofMillis(env("OTEL_METRIC_EXPORT_INTERVAL")?.toLongOrNull() ?: 15_000L),
            )

            private fun parseHeaders(raw: String?): Map<String, String> =
                raw?.split(",")
                    ?.mapNotNull { part ->
                        val idx = part.indexOf('=')
                        if (idx <= 0) null else part.substring(0, idx).trim() to part.substring(idx + 1).trim()
                    }
                    ?.toMap()
                    .orEmpty()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TelemetryService::class.java)
        private const val INSTRUMENTATION_SCOPE = "th.ac.bodin2.electives.api"
    }

    private val openTelemetry: OpenTelemetrySdk? = if (disabled) null else buildOpenTelemetry()

    private val meterRegistry: OtlpMeterRegistry? =
        if (disabled) null else OtlpMeterRegistry(otlpConfig(), Clock.SYSTEM)

    /** `null` when telemetry is disabled. */
    val tracer: Tracer? = openTelemetry?.getTracer(INSTRUMENTATION_SCOPE)

    private fun buildOpenTelemetry(): OpenTelemetrySdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            // These exporter selections are architectural constants of this app, not operator knobs:
            // traces + logs are exported over OTLP, while metrics are owned by Micrometer (so the SDK's
            // own metric exporter stays off to avoid duplicate metric pipelines). We hardcode them here
            // instead of via OTEL_* env vars so operators only ever set the endpoint/headers.
            .addPropertiesSupplier {
                mapOf(
                    "otel.traces.exporter" to "otlp",
                    "otel.logs.exporter" to "otlp",
                    "otel.metrics.exporter" to "none",
                    "otel.exporter.otlp.protocol" to "http/protobuf",
                )
            }
            // Make sure service.name is always set regardless
            .addResourceCustomizer { resource, _ ->
                resource.toBuilder()
                    .put(AttributeKey.stringKey("service.name"), config.serviceName)
                    .build()
            }
            // We manage this instance ourselves (passed to Ktor and Logback), so we deliberately
            // do NOT register it as the global instance to avoid clashes on dev-mode hot reloads
            .build()
            .openTelemetrySdk

    private fun otlpConfig() = object : OtlpConfig {
        override fun get(key: String): String? = null
        override fun url(): String = "${config.otlpEndpoint}/v1/metrics"
        override fun step(): Duration = config.metricStep
        override fun resourceAttributes(): Map<String, String> = mapOf("service.name" to config.serviceName)
        override fun headers(): Map<String, String> = config.otlpHeaders
    }

    /**
     * Installs the telemetry plugins on [app]. No-op when [disabled].
     *
     * **`KtorServerTelemetry` must be installed before any other logging/telemetry plugin**,
     * so this should be called first in the application module.
     */
    fun configure(app: Application) {
        val openTelemetry = openTelemetry ?: return
        val meterRegistry = meterRegistry ?: return

        // Bridge Logback logs into OpenTelemetry (exported to Loki via OTLP).
        OpenTelemetryAppender.install(openTelemetry)

        // Publish the tracer so dbQuery and the statement interceptor can create spans.
        Telemetry.tracer = tracer

        app.install(KtorServerTelemetry) {
            setOpenTelemetry(openTelemetry)
            capturedRequestHeaders(HttpHeaders.UserAgent)
        }

        app.install(MicrometerMetrics) {
            registry = meterRegistry
            meterBinders = listOf(
                JvmMemoryMetrics(),
                JvmGcMetrics(),
                JvmThreadMetrics(),
                ProcessorMetrics(),
                FileDescriptorMetrics(),
            )
        }

        logger.info("Telemetry enabled: exporting OTLP to ${config.otlpEndpoint} as '${config.serviceName}'")
    }

    /**
     * Runs [block] inside a span named [name].
     *
     * When telemetry is disabled this simply runs [block] with no span created.
     * The span's context is made current for the duration of [block] so any nested spans (including DB statement spans) will attach to it.
     */
    suspend fun <T> withSpan(name: String, kind: SpanKind = SpanKind.INTERNAL, block: suspend () -> T): T {
        val tracer = tracer ?: return block()

        val span = tracer.spanBuilder(name).setSpanKind(kind).startSpan()
        return try {
            withContext(Context.current().with(span).asContextElement()) { block() }
        } catch (e: Throwable) {
            span.recordException(e)
            span.setStatus(StatusCode.ERROR)
            throw e
        } finally {
            span.end()
        }
    }

    /** Flushes and shuts down exporters. No-op when [disabled]. */
    fun close() {
        Telemetry.tracer = null
        meterRegistry?.close()
        openTelemetry?.close()
    }
}
