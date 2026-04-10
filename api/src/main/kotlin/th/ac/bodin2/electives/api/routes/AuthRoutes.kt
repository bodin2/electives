package th.ac.bodin2.electives.api.routes

import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.api.RATE_LIMIT_AUTH
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.proto.api.AuthService
import th.ac.bodin2.electives.proto.api.AuthServiceKt.authenticateResponse

val authController = controller {
    val usersService: UsersService by dependencies

    routing {
        context(usersService) {
            rateLimit(RATE_LIMIT_AUTH) {
                post("/auth") { handleAuth() }

                authenticatedRoutes {
                    post("/logout") { handleLogOut() }
                }
            }
        }
    }
}

context(usersService: UsersService)
suspend fun RoutingContext.handleAuth() {
    val req = call.parseOrNull<AuthService.AuthenticateRequest>() ?: return badRequest()

    try {
        call.respond(authenticateResponse {
            @OptIn(Transactional::class)
            token = usersService.createSession(req.id, req.password, req.clientName)
        })
    } catch (e: Throwable) {
        when (e) {
            is EntityNotFoundException,
            is IllegalArgumentException -> {
                return unauthorized("Bad credentials")
            }

            else -> throw e
        }
    }
}

context(usersService: UsersService)
private suspend fun RoutingContext.handleLogOut() {
    authenticated { userId ->
        transaction { usersService.clearSession(userId) }
        ok()
    }
}