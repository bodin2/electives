package th.ac.bodin2.electives.api.utils

import com.squareup.wire.Message
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

class ClientException(val statusCode: HttpStatusCode, override val message: String? = null) : Exception(message) {
    // Client exceptions are expected to be thrown frequently, so we can skip filling in the stack trace for better performance
    override fun fillInStackTrace(): Throwable? = null
}

fun notFound(message: String? = null): ClientException = ClientException(HttpStatusCode.NotFound, message)
fun badRequest(message: String? = null): ClientException = ClientException(HttpStatusCode.BadRequest, message)
fun unauthorized(message: String? = null): ClientException = ClientException(HttpStatusCode.Unauthorized, message)
fun forbidden(message: String? = null): ClientException = ClientException(HttpStatusCode.Forbidden, message)
fun conflict(message: String? = null): ClientException = ClientException(HttpStatusCode.Conflict, message)

suspend inline fun RoutingContext.ok() {
    call.respond(HttpStatusCode.OK)
}

suspend inline fun RoutingContext.noContent() {
    call.respond(HttpStatusCode.NoContent)
}

suspend inline fun RoutingContext.created(proto: Message<*, *>) {
    call.respond(proto, HttpStatusCode.Created)
}

suspend inline fun WebSocketServerSession.badFrame(message: String? = null) {
    close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, message ?: "Bad Frame"))
}

suspend inline fun WebSocketServerSession.unauthorizedFrame(message: String? = null) {
    close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, message ?: "Unauthorized"))
}
