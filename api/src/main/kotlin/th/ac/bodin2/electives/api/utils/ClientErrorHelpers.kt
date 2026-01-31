package th.ac.bodin2.electives.api.utils

import com.google.protobuf.MessageLite
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

suspend inline fun RoutingContext.badRequest(message: String? = null) {
    call.response.status(HttpStatusCode.BadRequest)
    message?.let { call.respondText(it) }
}

suspend inline fun WebSocketServerSession.badFrame(message: String? = null) {
    close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, message ?: "Bad Frame"))
}

suspend inline fun WebSocketServerSession.unauthorized(message: String? = null) {
    close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, message ?: "Unauthorized"))
}

class ErrorResponse(val status: HttpStatusCode, val message: String?)

sealed class RouteResponse
data class Err(val response: ErrorResponse) : RouteResponse()
data class Ok(val response: MessageLite) : RouteResponse()

suspend inline fun RoutingContext.error(response: ErrorResponse) {
    call.response.status(response.status)
    response.message?.let { call.respondText(it) }
}

suspend inline fun RoutingContext.notFound(message: String? = null) {
    call.response.status(HttpStatusCode.NotFound)
    message?.let { call.respondText(it) }
}

suspend inline fun RoutingContext.unauthorized(message: String? = null) {
    call.response.status(HttpStatusCode.Unauthorized)
    message?.let { call.respondText(it) }
}

suspend inline fun RoutingContext.forbidden(message: String? = null) {
    call.response.status(HttpStatusCode.Forbidden)
    message?.let { call.respondText(it) }
}

suspend inline fun RoutingContext.conflict(message: String? = null) {
    call.response.status(HttpStatusCode.Conflict)
    message?.let { call.respondText(it) }
}