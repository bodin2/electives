package th.ac.bodin2.electives.api.services

import io.ktor.server.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import th.ac.bodin2.electives.proto.api.NotificationsService

interface NotificationsService {
    /**
     * A flow that holds latest bulk updates to be sent to connected clients.
     * **Bulk updates must always be sent on connection, then in an interval after.**
     *
     * Map<ElectiveId, Envelope>
     */
    val bulkUpdates: MutableStateFlow<Map<Int, NotificationsService.Envelope>>

    /**
     * Notifies connected clients about an update in subject selection.
     */
    fun notifySubjectSelectionUpdate(
        electiveId: Int,
        subjectId: Int,
        enrolledCount: Int,
    )

    /**
     * Starts the bulk update loop that periodically sends updates to connected clients.
     */
    fun startBulkUpdateLoop(): Job

    /**
     * Handles a new WebSocket connection for notifications.
     */
    suspend fun WebSocketServerSession.handleConnection(userId: Int)
}