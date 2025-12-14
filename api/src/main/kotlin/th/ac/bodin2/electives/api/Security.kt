package th.ac.bodin2.electives.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jetbrains.exposed.sql.transactions.transaction
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.utils.*
import java.security.Security
import kotlin.time.Duration.Companion.milliseconds

const val USER_AUTHENTICATION = "user"

fun Application.configureSecurity() {
    if (!isTest) {
        // Required for PASETO support (loading keys)
        Security.addProvider(BouncyCastleProvider())

        Paseto.init(
            Paseto.loadPublicKey(requireEnvNonBlank("PASETO_PUBLIC_KEY")),
            Paseto.loadPrivateKey(requireEnvNonBlank("PASETO_PRIVATE_KEY")),
            requireEnvNonBlank("PASETO_ISSUER")
        )

        Argon2.init(
            getEnv("ARGON2_MEMORY")?.toIntOrNull() ?: 65536,
            Argon2.findIterations(
                getEnv("ARGON2_AVG_TIME")?.toLongOrNull() ?: 500.milliseconds.inWholeMilliseconds
            )
        )
    }


    install(Authentication) {
        bearer(USER_AUTHENTICATION) {
            authenticate { tokenCredential ->
                try {
                    transaction { UsersService.getSessionUserId(tokenCredential.token) }
                } catch (e: Exception) {
                    println("Cannot authenticate user with token: ${tokenCredential.token}, cause: $e")
                    null
                }
            }
        }
    }
}