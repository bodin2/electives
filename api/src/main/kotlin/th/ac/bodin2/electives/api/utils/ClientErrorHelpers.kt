package th.ac.bodin2.electives.api.utils

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close

suspend inline fun RoutingContext.badRequest(message: String? = null) {
    call.response.status(HttpStatusCode.BadRequest)
    message?.let { call.respondText(it) }
}

suspend inline fun WebSocketServerSession.badFrame(message: String? = null) {
    close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, message ?: ""))
}

suspend inline fun RoutingContext.notFound(message: String? = null) {
    call.response.status(HttpStatusCode.NotFound)
    message?.let { call.respondText(it) }
}

suspend inline fun RoutingContext.unauthorized(message: String? = null) {
    call.response.status(HttpStatusCode.Unauthorized)
    message?.let { call.respondText(it) }
}

suspend inline fun WebSocketServerSession.unauthorized(message: String? = null) {
    call.response.status(HttpStatusCode.Unauthorized)
    message?.let { call.respondText(it) }
    close()
}

suspend inline fun RoutingContext.forbidden(message: String? = null) {
    call.response.status(HttpStatusCode.Forbidden)
    message?.let { call.respondText(it) }
}

suspend inline fun RoutingContext.conflict(message: String? = null) {
    call.response.status(HttpStatusCode.Conflict)
    message?.let { call.respondText(it) }
}