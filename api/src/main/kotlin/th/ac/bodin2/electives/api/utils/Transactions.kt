package th.ac.bodin2.electives.api.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/**
 * Runs [block] inside an Exposed transaction dispatched on [Dispatchers.IO].
 *
 * JDBC is blocking regardless of [org.jetbrains.exposed.v1.jdbc.transactions.transaction] or [suspendTransaction].
 *
 * Performance win comes entirely from [withContext]: it moves the blocking DB round-trips off Ktor's bounded
 * request-handling dispatcher onto the elastic IO pool, keeping the event-loop threads free.
 */
suspend fun <T> dbQuery(transactionIsolation: Int? = null, block: suspend JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) { suspendTransaction(transactionIsolation = transactionIsolation, statement = block) }
