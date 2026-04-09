package th.ac.bodin2.electives.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.di.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.api.services.AdminAuthService
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.services.isAdminAvailable
import th.ac.bodin2.electives.utils.Argon2
import th.ac.bodin2.electives.utils.KiB
import th.ac.bodin2.electives.utils.env
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds

fun DependencyRegistry.provideArgon2() {
    val memory = env("ARGON2_MEMORY")?.toIntOrNull()?.KiB?.toInt() ?: Argon2.DEFAULT_MEMORY
    val iterations = Argon2.findIterations(
        env("ARGON2_AVG_TIME")?.toIntOrNull()?.milliseconds ?: 500.milliseconds,
        memory,
    )

    provide<Argon2> { Argon2(memory, iterations) }
}

const val USER_AUTHENTICATION = "user"
const val ADMIN_AUTHENTICATION = "admin"

class UserPrincipal(val userId: Int)
class AdminPrincipal

fun Application.configureSecurity() {
    val usersService: UsersService by dependencies
    val app = this

    install(Authentication) {
        bearer(USER_AUTHENTICATION) {
            authenticate { tokenCredential -> usersService.toPrincipal(tokenCredential.token, this) }
        }

        if (app.isAdminAvailable) {
            bearer(ADMIN_AUTHENTICATION) {
                val adminAuthService: AdminAuthService by app.dependencies
                authenticate { tokenCredential -> adminAuthService.toPrincipal(tokenCredential.token, this) }
            }
        }
    }
}

fun AdminAuthService.toPrincipal(token: String, call: ApplicationCall): AdminPrincipal? {
    if (hasSession(token, call.request.origin.remoteAddress)) return AdminPrincipal()
    else {
        logger.debug("Cannot authenticate admin (address: ${call.request.origin.remoteAddress}, hash: ${token.toHash()})")
        return null
    }
}

fun UsersService.toPrincipal(token: String, call: ApplicationCall): UserPrincipal? =
    try {
        UserPrincipal(transaction { getSessionUserId(token) })
    } catch (e: Exception) {
        logger.debug("Cannot authenticate user (address: ${call.request.origin.remoteAddress}, hash: ${token.toHash()}): ${e.message}")
        null
    }

private fun String.toHash() =
    sha256Digest.get().apply { reset() }
        .digest(this.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private val sha256Digest = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }