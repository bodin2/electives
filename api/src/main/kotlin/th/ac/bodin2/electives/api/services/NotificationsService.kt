package th.ac.bodin2.electives.api.services

import io.ktor.server.websocket.*
import kotlinx.coroutines.Job

interface NotificationsService {
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
    suspend fun WebSocketServerSession.handleConnection()
}