package th.ac.bodin2.electives.api.routes

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.RATE_LIMIT_AUTH
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.proto.api.AuthService
import th.ac.bodin2.electives.proto.api.AuthServiceKt.authenticateResponse

fun Application.registerAuthRoutes() {
    val usersService: UsersService by dependencies

    routing {
        rateLimit(RATE_LIMIT_AUTH) {
            post("/auth") {
                val req = call.parseOrNull<AuthService.AuthenticateRequest>() ?: return@post badRequest()

                try {
                    call.respond(authenticateResponse {
                        @OptIn(CreatesTransaction::class)
                        token = usersService.createSession(req.id, req.password, req.clientName)
                    })
                } catch (e: Throwable) {
                    when (e) {
                        is NotFoundException,
                        is IllegalArgumentException -> {
                            return@post unauthorized("Bad credentials")
                        }

                        else -> throw e
                    }
                }
            }
        }

        authenticatedRoutes {
            post("/logout") {
                authenticated { userId ->
                    transaction { usersService.clearSession(userId) }
                    ok()
                }
            }
        }
    }
}