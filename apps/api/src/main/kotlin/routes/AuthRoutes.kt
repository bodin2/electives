package th.ac.bodin2.electives.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.routing.*
import th.ac.bodin2.electives.proto.api.AuthService
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.authenticated
import th.ac.bodin2.electives.api.utils.parse
import th.ac.bodin2.electives.api.utils.respondMessage
import th.ac.bodin2.electives.api.utils.unauthorized

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
                return@post unauthorized("Bad credentials")
            }
        }

        authenticated {
            post("/logout") {
                authenticated { userId ->
                    UsersService.clearSession(userId)
                    call.response.status(HttpStatusCode.OK)
                }
            }
        }
    }
}