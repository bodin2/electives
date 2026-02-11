package th.ac.bodin2.electives.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.di.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.api.services.AdminAuthService
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.connectingAddress
import th.ac.bodin2.electives.utils.Argon2
import th.ac.bodin2.electives.utils.getEnv
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds

const val USER_AUTHENTICATION = "user"
const val ADMIN_AUTHENTICATION = "admin"

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
            authenticate { tokenCredential -> usersService.toPrincipal(tokenCredential.token, this) }
        }

        if (this@configureSecurity.dependencies.contains(DependencyKey<AdminAuthService>()))
            bearer(ADMIN_AUTHENTICATION) {
                val adminAuthService: AdminAuthService by this@configureSecurity.dependencies
                authenticate { tokenCredential ->
                    if (adminAuthService.hasSession(tokenCredential.token, request.connectingAddress)) return@authenticate true
                    return@authenticate null
                }
            }
    }
}

fun UsersService.toPrincipal(token: String, call: ApplicationCall): Int? =
    try {
        transaction { getSessionUserId(token) }
    } catch (e: Exception) {
        val bytes = sha256Digest.get().apply { reset() }
            .digest(token.toByteArray(Charsets.UTF_8))

        val hash = bytes.joinToString("") { "%02x".format(it) }

        logger.debug("Cannot authenticate user (address: ${call.request.origin.remoteAddress}, hash: $hash): ${e.message}")
        null
    }

private val sha256Digest =
    ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }