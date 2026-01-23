package th.ac.bodin2.electives.api.services

import io.ktor.server.plugins.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.api.toPrincipal
import th.ac.bodin2.electives.api.utils.badFrame
import th.ac.bodin2.electives.api.utils.parseOrNull
import th.ac.bodin2.electives.api.utils.send
import th.ac.bodin2.electives.api.utils.unauthorized
import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.proto.api.NotificationsService.Envelope
import th.ac.bodin2.electives.proto.api.NotificationsService.Envelope.PayloadCase
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.acknowledged
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.bulkSubjectEnrollmentUpdate
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.envelope
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.subjectEnrollmentUpdate
import th.ac.bodin2.electives.utils.setInterval
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

private typealias SubjectSelectionUpdateListener = (electiveId: Int, subjectId: Int, enrolledCount: Int) -> Unit

class NotificationsServiceImpl(val config: Config, val usersService: UsersService) : NotificationsService {
    class Config(
        val maxSubjectSubscriptionsPerClient: Int,
        val bulkUpdateInterval: Duration,
        /**
         * Set to `false` to disable bulk updates (usually for testing purposes).
         */
        var bulkUpdatesEnabled: Boolean
    )

    companion object {
        private const val AUTHENTICATION_TIMEOUT_MILLISECONDS = 10_000L
        private val logger = LoggerFactory.getLogger(NotificationsServiceImpl::class.java)
    }

    /**
     * A flow that holds latest bulk updates to be sent to connected clients.
     * **Bulk updates must always be sent on connection, then in an interval after.**
     *
     * Map<ElectiveId, Envelope>
     */
    internal val bulkUpdates = MutableStateFlow<Map<Int, Envelope>>(emptyMap())

    private val bulkUpdateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val updateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val connections = ConcurrentHashMap<Int, ClientConnection>()

    private val subjectSelectionSubscriptions =
        mutableMapOf<Int, MutableMap<Int, MutableList<SubjectSelectionUpdateListener>>>()

    internal fun isBulkUpdatesEnabled() = config.bulkUpdatesEnabled

    override fun notifySubjectSelectionUpdate(
        electiveId: Int,
        subjectId: Int,
        enrolledCount: Int,
    ) {
        logger.debug("Notifying subject selection update, electiveId: $electiveId, subjectId: $subjectId, enrolledCount: $enrolledCount")

        val electiveSubscriptions = subjectSelectionSubscriptions[electiveId] ?: return
        val subjectListeners = electiveSubscriptions[subjectId] ?: return

        for (listener in subjectListeners) {
            listener(electiveId, subjectId, enrolledCount)
        }
    }

    override fun startBulkUpdateLoop() = bulkUpdateScope.launch {
        logger.info("Starting bulk enrollment update loop with interval: ${config.bulkUpdateInterval.inWholeMilliseconds}ms")

        setInterval(config.bulkUpdateInterval) {
            logger.debug("Bulk enrollment update loop tick, active connections: ${connections.size}")
            if (connections.isEmpty()) return@setInterval

            if (!isBulkUpdatesEnabled()) {
                logger.debug("Skipping bulk enrollment updates...")
                return@setInterval
            }

            val startMs = System.currentTimeMillis()
            logger.debug("Sending bulk enrollment updates...")

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

            logger.debug("Finished sending bulk updates in ${System.currentTimeMillis() - startMs}ms")
        }
    }

    override suspend fun WebSocketServerSession.handleConnection() {
        val userId = withTimeout(AUTHENTICATION_TIMEOUT_MILLISECONDS) {
            (incoming.receive() as? Frame.Binary)?.let {
                val token =
                    it.parseOrNull<Envelope>()?.identify?.token
                        ?: return@let null

                usersService.toPrincipal(token, call)
            }
        } ?: return unauthorized()

        logger.debug("Client connected, user: $userId, IP: ${call.request.origin.remoteHost}")

        connections[userId]?.let {
            logger.warn("Existing connection found for user: $userId, closing previous connection")
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

    private suspend fun ClientConnection.handleFrame(frame: Frame) {
        with(session) {
            if (frame !is Frame.Binary) return badFrame()

            val envelope = frame.parseOrNull<Envelope>() ?: return badFrame()

            when (envelope.payloadCase) {
                PayloadCase.SUBJECT_ENROLLMENT_UPDATE_SUBSCRIPTION_REQUEST -> {
                    val subscriptions = envelope
                        .subjectEnrollmentUpdateSubscriptionRequest
                        .subscriptionsMap

                    if (subscriptions
                            .map { (_, subscription) -> subscription.subjectIdsCount }
                            .sum() > config.maxSubjectSubscriptionsPerClient
                    )
                        return badFrame("Exceeded maximum subject subscriptions per client: ${config.maxSubjectSubscriptionsPerClient}")

                    subscriptions.forEach { (electiveId, subjectIds) ->
                        this@handleFrame.subscriptions
                            .getOrPut(electiveId) { ConcurrentHashMap.newKeySet() } += subjectIds.subjectIdsList
                    }

                    acknowledge(envelope)
                    logger.debug("Client subscriptions updated, user: $userId")

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
                PayloadCase.PAYLOAD_NOT_SET,
                PayloadCase.IDENTIFY -> return badFrame()
            }

            // Make sure it returns something
            return@with
        }
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

    private suspend fun ClientConnection.acknowledge(envelope: Envelope) {
        send(envelope {
            messageId = envelope.messageId
            acknowledged = acknowledged {}
        })
    }
}

private class ClientConnection(
    private val notificationsService: NotificationsServiceImpl,
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
    private val outgoing = Channel<Envelope>(
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
            var last: Map<Int, Envelope> = emptyMap()

            notificationsService.bulkUpdates.collect { current ->
                if (!notificationsService.isBulkUpdatesEnabled())
                    return@collect

                // ? This is generally fine for small numbers of electives.
                // ? If this becomes a bottleneck, move to per‑elective StateFlow.
                for ((id, update) in current)
                // Prevent sending duplicate updates. Protobuf message comparison is by‑value.
                    if (last[id] != update) send(update)

                last = current
            }
        }
    }

    suspend fun send(msg: Envelope) = outgoing.send(msg)

    fun trySend(msg: Envelope) = outgoing.trySend(msg)

    fun close() = scope.cancel()
}