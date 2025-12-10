package th.ac.bodin2.electives.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.transactions.transaction
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.proto.api.AuthService
import th.ac.bodin2.electives.proto.api.AuthServiceKt.authenticateResponse

fun Application.registerAuthRoutes() {
    routing {
        post("/auth") {
            val req = call.parseOrNull<AuthService.AuthenticateRequest>() ?: return@post badRequest()

            runCatching {
                call.respond(authenticateResponse {
                    token = transaction { UsersService.createSession(req.id, req.password, req.clientName) }
                })
            }.onFailure {
                when (it) {
                    is NotFoundException,
                    is IllegalArgumentException -> {
                        return@post unauthorized("Bad credentials")
                    }

                    else -> throw it
                }
            }
        }

        authenticatedRoutes {
            post("/logout") {
                authenticated { userId ->
                    transaction { UsersService.clearSession(userId) }
                    call.response.status(HttpStatusCode.OK)
                }
            }
        }
    }
}