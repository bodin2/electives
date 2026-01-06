package th.ac.bodin2.electives.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.utils.Argon2
import th.ac.bodin2.electives.utils.getEnv
import th.ac.bodin2.electives.utils.isTest
import kotlin.time.Duration.Companion.milliseconds

const val USER_AUTHENTICATION = "user"

fun Application.configureSecurity() {
    if (!isTest) {
        Argon2.init(
            getEnv("ARGON2_MEMORY")?.toIntOrNull() ?: 65536,
            getEnv("ARGON2_AVG_TIME")?.toIntOrNull()?.milliseconds ?: 500.milliseconds
        )
    }

    val usersService: UsersService by dependencies

    install(Authentication) {
        bearer(USER_AUTHENTICATION) {
            authenticate { tokenCredential -> usersService.toPrincipal(tokenCredential.token) }
        }
    }
}

fun UsersService.toPrincipal(token: String): Int? =
    try {
        transaction { getSessionUserId(token) }
    } catch (e: Exception) {
        println("Cannot authenticate user with token: $token, cause: $e")
        null
    }