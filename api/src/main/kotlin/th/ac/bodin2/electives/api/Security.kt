package th.ac.bodin2.electives.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jetbrains.exposed.sql.transactions.transaction
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.utils.Argon2
import th.ac.bodin2.electives.utils.Paseto
import th.ac.bodin2.electives.utils.getEnv
import th.ac.bodin2.electives.utils.isTest
import th.ac.bodin2.electives.utils.requireEnvNonBlank
import java.security.Security

const val USER_AUTHENTICATION = "user"

fun Application.configureSecurity() {
    // Required for PASETO support (loading keys)
    Security.addProvider(BouncyCastleProvider())

    if (!isTest) {
        Paseto.init(
            Paseto.loadPublicKey(requireEnvNonBlank("PASETO_PUBLIC_KEY")),
            Paseto.loadPrivateKey(requireEnvNonBlank("PASETO_PRIVATE_KEY")),
            requireEnvNonBlank("PASETO_ISSUER")
        )
    }

    Argon2.MEMORY = getEnv("ARGON2_MEMORY")?.toIntOrNull() ?: 65536
    Argon2.ITERATIONS = Argon2.findIterations(getEnv("ARGON2_AVG_TIME")?.toLongOrNull() ?: 500)

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