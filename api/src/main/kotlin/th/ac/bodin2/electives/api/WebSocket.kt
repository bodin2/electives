package th.ac.bodin2.electives.api

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import th.ac.bodin2.electives.utils.KiB
import kotlin.time.Duration.Companion.seconds

fun Application.configureWebSocket() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = 16.KiB
    }
}