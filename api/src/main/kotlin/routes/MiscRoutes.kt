package th.ac.bodin2.electives.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.registerMiscRoutes() {
    routing {
        head("/status") {
            call.respond(HttpStatusCode.OK)
        }
    }
}