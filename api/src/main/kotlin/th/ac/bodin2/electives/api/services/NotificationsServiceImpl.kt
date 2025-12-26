package th.ac.bodin2.electives.api.services

import io.ktor.server.plugins.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.api.utils.badFrame
import th.ac.bodin2.electives.api.utils.parseOrNull
import th.ac.bodin2.electives.api.utils.send
import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.proto.api.NotificationsService
import th.ac.bodin2.electives.proto.api.NotificationsService.Envelope.PayloadCase
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.acknowledged
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.bulkSubjectEnrollmentUpdate
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.envelope
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.subjectEnrollmentUpdate
import th.ac.bodin2.electives.utils.getEnv
import th.ac.bodin2.electives.utils.isTest
import th.ac.bodin2.electives.utils.setInterval
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
//@TODO: Cleanup
typealias SubjectSelectionUpdateListener = (electiveId: Int, subjectId: Int, enrolledCount: Int) -> Unit

private fun shouldSkipBulkUpdatesDuringTest() =
    isTest && getEnv("APP_TEST_NOTIFICATIONS_SERVICE_SEND_BULK_UPDATES").isNullOrBlank()

class NotificationsServiceImpl() : th.ac.bodin2.electives.api.services.NotificationsService {
    companion object {
        private val logger = LoggerFactory.getLogger(NotificationsService::class.java)
    }

    private val maxSubjectSubscriptionsPerClient =
        getEnv("NOTIFICATIONS_UPDATE_MAX_SUBSCRIPTIONS_PER_CLIENT")?.toIntOrNull() ?: 5
    private val bulkUpdateInterval =
        getEnv("NOTIFICATIONS_BULK_UPDATE_INTERVAL")?.toIntOrNull()?.milliseconds ?: 5.seconds

    private val bulkUpdateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val updateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val bulkUpdates = MutableStateFlow<Map<Int, NotificationsService.Envelope>>(emptyMap())

    private val connections = ConcurrentHashMap<Int, ClientConnection>()

    private val subjectSelectionSubscriptions =
        mutableMapOf<Int, MutableMap<Int, MutableList<SubjectSelectionUpdateListener>>>()

    override fun notifySubjectSelectionUpdate(
        electiveId: Int,
        subjectId: Int,
        enrolledCount: Int,
    ) {
        logger.info("Notifying subject selection update, electiveId: $electiveId, subjectId: $subjectId, enrolledCount: $enrolledCount")

        val electiveSubscriptions = subjectSelectionSubscriptions[electiveId] ?: return
        val subjectListeners = electiveSubscriptions[subjectId] ?: return

        for (listener in subjectListeners) {
            listener(electiveId, subjectId, enrolledCount)
        }
    }

    override fun startBulkUpdateLoop() = bulkUpdateScope.launch {
        logger.info("Starting bulk enrollment update loop with interval: ${bulkUpdateInterval.inWholeMilliseconds}ms")

        setInterval(bulkUpdateInterval) {
            logger.debug("Bulk enrollment update loop tick, active connections: ${connections.size}")
            if (connections.isEmpty()) return@setInterval

            if (shouldSkipBulkUpdatesDuringTest()) {
                logger.debug("Skipping bulk enrollment updates during test environment")
                return@setInterval
            }

            val startMs = System.currentTimeMillis()
            logger.info("Sending bulk enrollment updates...")

            val updates = transaction {
                Elective.allReferences().map { elective ->
                    val enrolledCounts = Elective.getSubjectsEnrolledCounts(elective)

                    envelope {
                        bulkSubjectEnrollmentUpdate = bulkSubjectEnrollmentUpdate {
                            electiveId = elective.id
                            subjectEnrolledCounts.putAll(enrolledCounts)
                        }
                    }
                }
            }

            bulkUpdates.update { current ->
                current + updates.associateBy { it.bulkSubjectEnrollmentUpdate.electiveId }
            }

            logger.info("Finished sending bulk updates in ${System.currentTimeMillis() - startMs}ms")
        }
    }

    override suspend fun WebSocketServerSession.handleConnection(userId: Int) {
        logger.info("Client connected, user: $userId, IP: ${call.request.origin.remoteHost}")

        connections.get(userId)?.let {
            logger.info("Existing connection found for user: $userId, closing previous connection")
            it.close()
        }

        val connection = ClientConnection(
            this@NotificationsServiceImpl,
            userId = userId,
            session = this,
            subscriptions = ConcurrentHashMap(),
            cleanups = ConcurrentHashMap.newKeySet(),
            parentScope = updateScope,
        )

        connections[userId] = connection

        try {
            for (frame in incoming) connection.handleFrame(frame)
        } finally {
            connection.close()
            connections.remove(userId)
        }
    }

    private suspend fun ClientConnection.handleFrame(frame: Frame) = with(session) {
        if (frame !is Frame.Binary) return badFrame()

        val envelope = frame.parseOrNull<NotificationsService.Envelope>() ?: return badFrame()

        when (envelope.payloadCase) {
            PayloadCase.SUBJECT_ENROLLMENT_UPDATE_SUBSCRIPTION_REQUEST -> {
                val subscriptions = envelope
                    .subjectEnrollmentUpdateSubscriptionRequest
                    .subscriptionsMap

                if (subscriptions
                        .map { (_, subscription) -> subscription.subjectIdsCount }
                        .sum() > maxSubjectSubscriptionsPerClient
                )
                    return badFrame("Exceeded maximum subject subscriptions per client: $maxSubjectSubscriptionsPerClient")

                subscriptions.forEach { (electiveId, subjectIds) ->
                    this@handleFrame.subscriptions
                        .getOrPut(electiveId) { ConcurrentHashMap.newKeySet() } += subjectIds.subjectIdsList
                }

                acknowledge(envelope)
                logger.info("Client subscriptions updated, user: $userId")

                // Cleanup previous enrollment updates
                for (cleanup in cleanups) runCatching { cleanup() }.onFailure {
                    logger.error(
                        "Error during elective enrollment subscription update:",
                        it
                    )
                }
                cleanups.clear()

                // Listen for enrollment updates the client requested
                cleanups += sendEnrollmentUpdates()
            }

            // Server payloads
            PayloadCase.SUBJECT_ENROLLMENT_UPDATE, PayloadCase.BULK_SUBJECT_ENROLLMENT_UPDATE, PayloadCase.ACKNOWLEDGED ->
                return badFrame()

            // Invalid payload
            PayloadCase.PAYLOAD_NOT_SET -> return badFrame()
        }

        // Make sure it returns something
        return@with
    }

    private fun ClientConnection.sendEnrollmentUpdates(): () -> Unit {
        val listener: SubjectSelectionUpdateListener = { electiveId, subjectId, enrolledCount ->
            trySend(envelope {
                subjectEnrollmentUpdate = subjectEnrollmentUpdate {
                    this.electiveId = electiveId
                    this.subjectId = subjectId
                    this.enrolledCount = enrolledCount
                }
            })
        }

        for ((electiveId, subjectIds) in subscriptions) {
            val electiveSubscriptions = subjectSelectionSubscriptions.getOrPut(electiveId) { mutableMapOf() }
            for (subjectId in subjectIds) {
                val listeners = electiveSubscriptions.getOrPut(subjectId) { mutableListOf() }
                listeners.add(listener)
            }
        }

        return {
            for ((electiveId, subjectIds) in subscriptions) {
                val electiveSubscriptions = subjectSelectionSubscriptions[electiveId]
                for (subjectId in subjectIds)
                    electiveSubscriptions?.get(subjectId)?.remove(listener)
            }
        }
    }

    private suspend fun ClientConnection.acknowledge(envelope: NotificationsService.Envelope) {
        send(envelope {
            messageId = envelope.messageId
            acknowledged = acknowledged {}
        })
    }
}

private class ClientConnection(
    private val notificationsService: th.ac.bodin2.electives.api.services.NotificationsService,
    val userId: Int,
    val session: WebSocketServerSession,
    // Map<ElectiveId, List<SubjectId>>
    val subscriptions: ConcurrentHashMap<Int, ConcurrentHashMap.KeySetView<Int, Boolean>>,
    val cleanups: ConcurrentHashMap.KeySetView<() -> Unit, Boolean>,
    parentScope: CoroutineScope,
) {
    companion object {
        private const val DROPPED_LIMIT = 32
        private val logger = LoggerFactory.getLogger(ClientConnection::class.java)

        private fun ClientConnection.handleUndeliveredElement() {
            @OptIn(DelicateCoroutinesApi::class)
            if (!outgoing.isClosedForSend) {
                val dropped = droppedCount.incrementAndGet()
                logger.warn("Dropped envelope for user: $userId, dropped so far: $dropped")

                if (dropped >= DROPPED_LIMIT) {
                    logger.error("Dropped envelope limit reached for user: $userId, closing connection")
                    scope.cancel()
                }
            }
        }
    }

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val droppedCount = AtomicInteger(0)

    // One channel per client to buffer outgoing messages, to prevent concurrent sends/writes
    private val outgoing = Channel<NotificationsService.Envelope>(
        16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { handleUndeliveredElement() }
    )

    init {
        val job = scope.coroutineContext[Job]!!

        job.invokeOnCompletion {
            scope.launch(NonCancellable) {
                runCatching {
                    session.close()
                }.onFailure {
                    logger.error("Error closing WebSocket session for user: $userId", it)
                }
            }

            for (cleanup in cleanups) runCatching { cleanup() }.onFailure {
                logger.error("Error during ClientConnection close:", it)
            }
        }

        scope.launch {
            for (msg in outgoing) session.send(msg)
        }

        scope.launch {
            var last: Map<Int, NotificationsService.Envelope> = emptyMap()

            notificationsService.bulkUpdates.collect { current ->
                if (shouldSkipBulkUpdatesDuringTest()) {
                    logger.warn("Skipping sending bulk updates during test environment")
                    return@collect
                }

                // ? This is generally fine for small numbers of electives.
                // ? If this becomes a bottleneck, move to per‑elective StateFlow.
                for ((id, update) in current)
                    if (last[id] != update) send(update)

                last = current
            }
        }
    }

    suspend fun send(msg: NotificationsService.Envelope) = outgoing.send(msg)

    fun trySend(msg: NotificationsService.Envelope) = outgoing.trySend(msg)

    fun close() = scope.cancel()
}