package th.ac.bodin2.electives.api.utils

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi

/**
 * An Exposed [StatementInterceptor] that opens one span per SQL statement executed within a transaction, giving per-statement visibility.
 */
class StatementSpanInterceptor(private val tracer: Tracer) : StatementInterceptor {
    private val pending = ArrayDeque<Span>()

    override fun beforeExecution(transaction: Transaction, context: StatementContext) {
        val span = tracer.spanBuilder("db.statement")
            .setSpanKind(SpanKind.CLIENT)
            .setParent(Context.current())
            .startSpan()

        try {
            span.setAttribute("db.operation", context.statement.type.toString())
            // No bound parameters are available here, which is good
            span.setAttribute("db.statement", context.sql(transaction))
        } catch (_: Throwable) {}

        pending.addLast(span)
    }

    override fun afterExecution(
        transaction: Transaction,
        contexts: List<StatementContext>,
        executedStatement: PreparedStatementApi,
    ) {
        pending.removeLastOrNull()?.end()
    }

    /** Ends any spans left open (e.g. when a statement threw before [afterExecution] ran). */
    fun endPending() {
        while (pending.isNotEmpty()) pending.removeLast().end()
    }
}
