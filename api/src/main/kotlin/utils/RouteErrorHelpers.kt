package th.ac.bodin2.electives.api.utils

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext

suspend inline fun RoutingContext.badRequest(message: String? = null) {
    call.response.status(HttpStatusCode.BadRequest)
    message?.let { call.respondText(it) }
}

suspend inline fun RoutingContext.notFound(message: String? = null) {
    call.response.status(HttpStatusCode.NotFound)
    message?.let { call.respondText(it) }
}

suspend inline fun RoutingContext.unauthorized(message: String? = null) {
    call.response.status(HttpStatusCode.Unauthorized)
    message?.let { call.respondText(it) }
}