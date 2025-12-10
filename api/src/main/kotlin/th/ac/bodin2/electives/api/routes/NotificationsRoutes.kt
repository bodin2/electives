package th.ac.bodin2.electives.api.routes

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.collections.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.transactions.transaction
import th.ac.bodin2.electives.api.services.SubjectSelectionUpdateListener
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.proto.api.NotificationsService
import th.ac.bodin2.electives.proto.api.NotificationsService.Envelope.PayloadCase
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.acknowledged
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.bulkSubjectEnrollmentUpdate
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.envelope
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.subjectEnrollmentUpdate
import th.ac.bodin2.electives.utils.setInterval
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

val clients = ConcurrentSet<WebSocketServerSession>()

// UserId -> Map<ElectiveId, List<SubjectId>>
val clientSubscriptions = ConcurrentHashMap<WebSocketServerSession, MutableMap<Int, List<Int>>>()
val clientCleanups = ConcurrentHashMap<WebSocketServerSession, () -> Unit>()

private const val MAX_SUBJECTS_SUBSCRIPTIONS_PER_CLIENT = 5
private val BULK_UPDATE_INTERVAL = 5.seconds

fun Application.registerNotificationsRoutes() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        // 16 KB
        maxFrameSize = 16384
        masking = false
    }

    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch {
        setInterval(BULK_UPDATE_INTERVAL) {
            if (clients.isEmpty()) return@setInterval

            val updates = transaction {
                Elective.getAllIds().map { electiveId ->
                    val enrolledCounts = Elective.getSubjectsEnrolledCounts(electiveId)

                    envelope {
                        bulkSubjectEnrollmentUpdate = bulkSubjectEnrollmentUpdate {
                            this.electiveId = electiveId
                            this.subjectEnrolledCounts.putAll(enrolledCounts)
                        }
                    }
                }
            }

            for (client in clients)
                for (update in updates) client.send(update)
        }
    }

    routing {
        authenticatedRoutes {
            webSocket("/notifications") {
                clients.add(this)

                try {
                    for (frame in incoming) handleFrame(frame)
                } finally {
                    // Cleanup on disconnect
                    clients.remove(this)
                    clientSubscriptions.remove(this)
                    clientCleanups.remove(this)?.invoke()
                }
            }
        }
    }
}

private suspend fun WebSocketServerSession.handleFrame(frame: Frame) {
    if (frame !is Frame.Binary) return badFrame()

    val envelope = frame.parseOrNull<NotificationsService.Envelope>() ?: return badFrame()

    when (envelope.payloadCase) {
        PayloadCase.SUBJECT_ENROLLMENT_UPDATE_SUBSCRIPTION_REQUEST -> {
            val subscriptions = envelope
                .subjectEnrollmentUpdateSubscriptionRequest
                .subscriptionsMap

            if (subscriptions
                    .map { (_, subscription) -> subscription.subjectIdsCount }
                    .sum() > MAX_SUBJECTS_SUBSCRIPTIONS_PER_CLIENT
            )
                return badFrame("Exceeded maximum subject subscriptions per client: $MAX_SUBJECTS_SUBSCRIPTIONS_PER_CLIENT")

            subscriptions.forEach { (electiveId, subjectIds) ->
                clientSubscriptions
                    .getOrPut(this) { mutableMapOf() }[electiveId] =
                    subjectIds.subjectIdsList
            }

            acknowledge(envelope)

            // ? In the future, if we need more cleanups, simply wrap it in one big lambda
            // Send the immediate updates the client requested
            clientCleanups[this] = sendClientEnrollmentUpdates()
        }

        // Server payloads
        PayloadCase.SUBJECT_ENROLLMENT_UPDATE, PayloadCase.BULK_SUBJECT_ENROLLMENT_UPDATE, PayloadCase.ACKNOWLEDGED ->
            return badFrame()

        // Invalid payload
        PayloadCase.PAYLOAD_NOT_SET -> return badFrame()
    }
}

suspend fun WebSocketSession.acknowledge(envelope: NotificationsService.Envelope) {
    send(envelope {
        messageId = envelope.messageId
        acknowledged = acknowledged {}
    })
}

private fun WebSocketServerSession.sendClientEnrollmentUpdates(): () -> Unit {
    val subscriptions = clientSubscriptions[this]
        ?: throw IllegalStateException("Client subscriptions $clientSubscriptions not found")

    val listener: SubjectSelectionUpdateListener = { enrolledCount ->
        scope.launch {
            send(envelope {
                subjectEnrollmentUpdate = subjectEnrollmentUpdate {
                    this.electiveId = electiveId
                    this.subjectId = subjectId
                    this.enrolledCount = enrolledCount
                }
            })
        }
    }

    for ((electiveId, subjectIds) in subscriptions)
        for (subjectId in subjectIds)
            UsersService.subscribeToSubjectSelections(electiveId, subjectId, listener)

    return {
        for ((electiveId, subjectIds) in subscriptions)
            for (subjectId in subjectIds)
                UsersService.unsubscribeFromSubjectSelections(electiveId, subjectId, listener)
    }
}

private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())