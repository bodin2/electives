package th.ac.bodin2.electives.api

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import th.ac.bodin2.electives.api.services.UsersService

const val USER_AUTHENTICATION = "user"

fun Application.configureSecurity() {
    install(Authentication) {
        bearer(USER_AUTHENTICATION) {
            authenticate { tokenCredential ->
                try {
                    val id = UsersService.getSessionUserId(tokenCredential.token)
                    id
                } catch (e: Exception) {
                    println("Cannot authenticate user with token: ${tokenCredential.token}, cause: $e")
                    null
                }
            }
        }
    }
}