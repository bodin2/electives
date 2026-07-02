package th.ac.bodin2.electives.api.utils

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import th.ac.bodin2.electives.api.services.Telemetry

/**
 * Runs [block] inside an Exposed transaction dispatched on [Dispatchers.IO].
 *
 * JDBC is blocking regardless of [org.jetbrains.exposed.v1.jdbc.transactions.transaction] or [suspendTransaction].
 *
 * Performance win comes entirely from [withContext]: it moves the blocking DB round-trips off Ktor's bounded
 * request-handling dispatcher onto the elastic IO pool, keeping the event-loop threads free.
 *
 * ## Tracing
 *
 * When telemetry is enabled (see [th.ac.bodin2.electives.api.services.TelemetryService]),
 * this opens a `db.query` span for the whole [block] (optionally named via [name]) and registers a
 * [StatementSpanInterceptor] so every SQL statement executed inside gets its own child span.
 *
 * The OpenTelemetry context is propagated across the [withContext] dispatch, so those spans attach to the request span.
 *
 * When telemetry is disabled ([Telemetry.tracer] is `null`), this takes a fast path identical to a plain `suspendTransaction`.
 */
suspend fun <T> dbQuery(
    transactionIsolation: Int? = null,
    name: String? = null,
    block: suspend JdbcTransaction.() -> T,
): T {
    val tracer = Telemetry.tracer
        ?: return withContext(Dispatchers.IO) {
            suspendTransaction(transactionIsolation = transactionIsolation, statement = block)
        }

    val span = tracer.spanBuilder(name ?: "db.query").setSpanKind(SpanKind.CLIENT).startSpan()
    val interceptor = StatementSpanInterceptor(tracer)

    return try {
        withContext(Dispatchers.IO + Context.current().with(span).asContextElement()) {
            suspendTransaction(transactionIsolation = transactionIsolation) {
                registerInterceptor(interceptor)
                block()
            }
        }
    } catch (e: Throwable) {
        span.recordException(e)
        span.setStatus(StatusCode.ERROR)
        throw e
    } finally {
        interceptor.endPending()
        span.end()
    }
}
