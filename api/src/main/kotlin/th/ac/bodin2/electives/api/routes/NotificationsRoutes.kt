package th.ac.bodin2.electives.api.routes

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import th.ac.bodin2.electives.api.services.NotificationsService
import th.ac.bodin2.electives.api.services.NotificationsService.handleConnection
import th.ac.bodin2.electives.api.utils.authenticated
import th.ac.bodin2.electives.api.utils.authenticatedRoutes
import th.ac.bodin2.electives.utils.KiB
import th.ac.bodin2.electives.utils.isDev
import th.ac.bodin2.electives.utils.isTest
import kotlin.time.Duration.Companion.seconds

fun Application.registerNotificationsRoutes() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = 16.KiB
        masking = !isDev && !isTest
    }

    NotificationsService.startBulkUpdateLoop()

    routing {
        authenticatedRoutes {
            webSocket("/notifications") {
                authenticated { userId ->
                    handleConnection(userId)
                }
            }
        }
    }
}