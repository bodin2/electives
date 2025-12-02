package th.ac.bodin2.electives.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import th.ac.bodin2.electives.proto.api.AuthService
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.authenticated
import th.ac.bodin2.electives.api.utils.parse
import th.ac.bodin2.electives.api.utils.respondMessage

fun Application.registerAuthRoutes() {
    routing {
        post("/auth") {
            val req = call.parse<AuthService.AuthenticateRequest>()

            try {
                val token = UsersService.createSession(req.id, req.password, req.clientName)

                call.respondMessage(
                    AuthService.AuthenticateResponse.newBuilder()
                        .setToken(token)
                        .build()
                )
            } catch (_: IllegalArgumentException) {
                call.respondText("Bad credentials", status = HttpStatusCode.Unauthorized)
            }
        }

        authenticate("auth-user") {
            post("/logout") {
                authenticated { userId ->
                    UsersService.clearSession(userId)
                    call.response.status(HttpStatusCode.OK)
                }
            }
        }
    }
}