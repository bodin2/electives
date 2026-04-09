package th.ac.bodin2.electives.api.routes

import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import th.ac.bodin2.electives.api.RATE_LIMIT_NOTIFICATIONS
import th.ac.bodin2.electives.api.services.NotificationsService

val notificationsController = controller {
    val notificationsService: NotificationsService by dependencies
    notificationsService.startBulkUpdateLoop()

    routing {
        rateLimit(RATE_LIMIT_NOTIFICATIONS) {
            webSocket("/notifications") {
                notificationsService.apply { handleConnection() }
            }
        }
    }
}