package th.ac.bodin2.electives.api.services

import io.ktor.server.plugins.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.api.isTest
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

// Fake ID for admin sessions
private const val ADMIN_PSEUDO_ID = -1

private typealias SubjectSelectionUpdateListener = (electiveId: Int, subjectId: Int, enrolledCount: Int) -> Unit

class NotificationsServiceImpl(
    val config: Config,
    val usersService: UsersService,
    val adminAuthService: AdminAuthService? = null,
) : NotificationsService {
    class Config(
        val maxSubjectSubscriptionsPerClient: Int,
        val bulkUpdateInterval: Duration,
        /**
         * Set to `false` to disable bulk updates (usually for testing purposes).
         */
        var bulkUpdatesEnabled: Boolean,
    )

    companion object {
        private const val AUTHENTICATION_TIMEOUT_MILLISECONDS = 10_000L
        private val logger = LoggerFactory.getLogger(NotificationsServiceImpl::class.java)
    }

    /**
     * A StateFlow of the map of per-elective StateFlows.
     * **Bulk updates must always be sent on connection, then in an interval after.**
     *
     * Wrapping in a StateFlow allows [ClientConnection] to react to new electives
     * being added after a client has already connected.
     *
     * Map<ElectiveId, StateFlow<Envelope?>>
     */
    internal val bulkUpdateFlows = MutableStateFlow<Map<Int, MutableStateFlow<Envelope?>>>(emptyMap())

    private val bulkUpdateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val updateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val connections = ConcurrentHashMap<Int, ClientConnection>()

    private val subjectSelectionSubscriptions =
        ConcurrentHashMap<Int, ConcurrentHashMap<Int, CopyOnWriteArrayList<SubjectSelectionUpdateListener>>>()

    internal fun isBulkUpdatesEnabled() = config.bulkUpdatesEnabled

    internal fun bulkUpdateFlowForElective(electiveId: Int): StateFlow<Envelope?> =
        bulkUpdateFlows.value[electiveId] ?: MutableStateFlow<Envelope?>(null).also { newFlow ->
            bulkUpdateFlows.update { current ->
                if (electiveId in current) current
                else current + (electiveId to newFlow)
            }
        }

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
            if (!isTest) logger.debug("Bulk enrollment update loop tick, active connections: ${connections.size}")
            if (connections.isEmpty()) return@setInterval

            if (!isBulkUpdatesEnabled()) {
                logger.debug("Skipping bulk enrollment updates...")
                return@setInterval
            }

            val startMs = System.currentTimeMillis()
            logger.debug("Sending bulk enrollment updates...")

            val updates = transaction {
                Elective.allActiveReferences().map { elective ->
                    val enrolledCounts = Elective.getSubjectsEnrolledCounts(elective)

                    elective.id to envelope {
                        bulkSubjectEnrollmentUpdate = bulkSubjectEnrollmentUpdate {
                            electiveId = elective.id
                            subjectEnrolledCounts.putAll(enrolledCounts)
                        }
                    }
                }
            }

            for ((electiveId, update) in updates) {
                val flow = bulkUpdateFlowForElective(electiveId) as MutableStateFlow
                // Update the flow only if the updated value changes
                if (flow.value != update) flow.value = update
            }

            logger.debug("Finished sending bulk updates in ${System.currentTimeMillis() - startMs}ms")
        }
    }

    private suspend fun WebSocketServerSession.handleSession(userId: Int) {
        logger.debug("Client connected, user: $userId, IP: ${call.request.origin.remoteHost}")

        val connection = ClientConnection(
            notificationsService = this@NotificationsServiceImpl,
            userId = userId,
            session = this,
            subscriptions = ConcurrentHashMap(),
            cleanups = ConcurrentHashMap.newKeySet(),
            parentScope = updateScope,
        )

        connection.start()
        connections.put(userId, connection)?.let {
            logger.warn("Existing connection found for user: $userId, closing previous connection")
            it.close()
        }

        try {
            for (frame in incoming) connection.handleFrame(frame)
        } finally {
            connection.close()
            connections.remove(userId, connection)
            logger.debug("Client disconnected, user: $userId")
        }
    }

    override suspend fun WebSocketServerSession.handleConnection() {
        val userId = try {
            withTimeout(AUTHENTICATION_TIMEOUT_MILLISECONDS) {
                (incoming.receive() as? Frame.Binary)?.let {
                    val token =
                        it.parseOrNull<Envelope>()?.identify?.token
                            ?: return@withTimeout null

                    usersService.toPrincipal(token, call)?.userId
                }
            } ?: throw IllegalArgumentException()
        } catch (e: Exception) {
            when (e) {
                // Client authentication failures
                is CancellationException,
                is IllegalArgumentException,
                is NotFoundException -> {
                }

                else -> logger.error("Error during authentication from IP: ${call.request.origin.remoteHost}", e)
            }

            return unauthorized()
        }

        handleSession(userId)
    }

    override suspend fun WebSocketServerSession.handleAdminConnection() {
        adminAuthService ?: throw IllegalStateException("Cannot get AdminAuthService")

        try {
            withTimeout(AUTHENTICATION_TIMEOUT_MILLISECONDS) {
                val authenticated = (incoming.receive() as? Frame.Binary)?.let {
                    val token =
                        it.parseOrNull<Envelope>()?.identify?.token
                            ?: return@withTimeout null

                    adminAuthService.hasSession(token, call.request.origin.remoteAddress)
                }

                if (authenticated != true) throw IllegalArgumentException()
            }
        } catch (e: Exception) {
            when (e) {
                // Client authentication failures
                is IllegalArgumentException,
                is TimeoutCancellationException -> {
                }

                else -> logger.error("Error during authentication from IP: ${call.request.origin.remoteAddress}", e)
            }

            return unauthorized()
        }

        handleSession(ADMIN_PSEUDO_ID)
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

                    // Cleanup previous enrollment update listeners
                    for (cleanup in cleanups) try {
                        cleanup()
                    } catch (e: Exception) {
                        logger.error("Error during elective enrollment subscription update:", e)
                    }

                    cleanups.clear()

                    // Listen for enrollment updates the client requested
                    cleanups += sendEnrollmentUpdates()
                }

                // Server payloads
                PayloadCase.SUBJECT_ENROLLMENT_UPDATE,
                PayloadCase.BULK_SUBJECT_ENROLLMENT_UPDATE,
                PayloadCase.ACKNOWLEDGED -> return badFrame()

                // Invalid payload
                PayloadCase.PAYLOAD_NOT_SET,
                PayloadCase.IDENTIFY -> return badFrame()
            }
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
            val electiveSubscriptions = subjectSelectionSubscriptions.getOrPut(electiveId) { ConcurrentHashMap() }
            for (subjectId in subjectIds) {
                val listeners = electiveSubscriptions.getOrPut(subjectId) { CopyOnWriteArrayList() }
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
    // Map<ElectiveId, Set<SubjectId>>
    val subscriptions: ConcurrentHashMap<Int, ConcurrentHashMap.KeySetView<Int, Boolean>>,
    val cleanups: ConcurrentHashMap.KeySetView<() -> Unit, Boolean>,
    parentScope: CoroutineScope,
) {
    companion object {
        private const val DROPPED_LIMIT = 32
        private val logger = LoggerFactory.getLogger(ClientConnection::class.java)

        private fun ClientConnection.handleUndeliveredElement() {
            @OptIn(DelicateCoroutinesApi::class)
            // This could return false, and later during dropped comparison return true
            // But that's fine, we only filter for log noise, closing a closed channel is a no-op
            if (outgoing.isClosedForSend) return

            val dropped = droppedCount.incrementAndGet()
            logger.warn("Dropped envelope for user: $userId, dropped so far: $dropped")

            if (dropped >= DROPPED_LIMIT) {
                logger.error("Dropped envelope limit reached for user: $userId, closing connection")
                close()
            }
        }
    }

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val droppedCount = AtomicInteger(0)

    // One channel per client to buffer outgoing messages, to prevent concurrent sends/writes
    private val outgoing = Channel<Envelope>(
        16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { handleUndeliveredElement() },
    )

    fun start() {
        scope.launch {
            try {
                for (msg in outgoing) session.send(msg)
            } finally {
                cleanup()
            }
        }

        // Collect the outer StateFlow so we react to both existing elective flows
        // and any new ones added after this client connected
        scope.launch {
            val active = mutableMapOf<Int, Job>()
            notificationsService.bulkUpdateFlows.collect { flows ->
                // Cancel collectors for electives that have been removed
                val removed = active.keys - flows.keys
                removed.forEach { active.remove(it)?.cancel() }

                // Launch a collector for any newly added elective flow
                for ((electiveId, flow) in flows) {
                    if (electiveId in active) continue

                    active[electiveId] = scope.launch {
                        var last: Envelope? = null
                        flow.collect { update ->
                            if (!notificationsService.isBulkUpdatesEnabled()) return@collect
                            if (update == null || update === last) return@collect
                            last = update
                            trySend(update)
                        }
                    }
                }
            }
        }
    }

    // This can be called multiple times in the right conditions, but that's fine, closing a closed session/channel is a no-op
    private suspend fun cleanup() {
        try {
            session.close()
        } catch (e: Exception) {
            logger.error("Error during ClientConnection WebSocket close: $userId", e)
        }

        for (cleanup in cleanups) try {
            cleanup()
        } catch (e: Exception) {
            logger.error("Error during ClientConnection close:", e)
        }

        scope.cancel()
    }

    suspend fun send(msg: Envelope) = outgoing.send(msg)

    fun trySend(msg: Envelope) = outgoing.trySend(msg)

    fun close() = outgoing.close()
}