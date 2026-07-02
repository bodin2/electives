package th.ac.bodin2.electives.api.routes

import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.api.RATE_LIMIT_AUTH
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.proto.api.AuthService

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
    val req = call.parseOrNull<AuthService.AuthenticateRequest>() ?: throw badRequest()

    val token = try {
        @OptIn(Transactional::class)
        usersService.createSession(req.id, req.password, req.client_name)
    } catch (_: EntityNotFoundException) {
        throw unauthorized("Bad credentials")
    } catch (_: IllegalArgumentException) {
        throw unauthorized("Bad credentials")
    }
    call.respond(AuthService.AuthenticateResponse(token = token))
}

context(usersService: UsersService)
private suspend fun RoutingContext.handleLogOut() {
    authenticated { user ->
        dbQuery { usersService.clearSession(user.id) }
        ok()
    }
}