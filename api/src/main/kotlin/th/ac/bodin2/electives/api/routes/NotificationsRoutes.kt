package th.ac.bodin2.electives.api.routes

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import th.ac.bodin2.electives.api.RATE_LIMIT_NOTIFICATIONS
import th.ac.bodin2.electives.api.services.NotificationsService
import th.ac.bodin2.electives.utils.KiB
import kotlin.time.Duration.Companion.seconds

fun Application.registerNotificationsRoutes() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = 16.KiB
    }

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