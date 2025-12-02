package th.ac.bodin2.electives.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.head
import io.ktor.server.routing.routing

fun Application.registerMiscRoutes() {
    routing {
        head("/status") {
            call.respond(HttpStatusCode.OK)
        }
    }
}