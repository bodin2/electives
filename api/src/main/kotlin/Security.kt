package th.ac.bodin2.electives.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.utils.Argon2
import th.ac.bodin2.electives.utils.getEnv

const val USER_AUTHENTICATION = "user"

fun Application.configureSecurity() {
    Argon2.MEMORY = getEnv("ARGON2_MEMORY")?.toIntOrNull() ?: 65536
    Argon2.ITERATIONS = Argon2.findIterations(getEnv("ARGON2_AVG_TIME")?.toLongOrNull() ?: 500)

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