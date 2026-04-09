package th.ac.bodin2.electives.api.services

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.api.contains
import th.ac.bodin2.electives.api.logger
import th.ac.bodin2.electives.api.services.AdminAuthService.CreateSessionResult
import th.ac.bodin2.electives.utils.*
import java.security.*
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val isAdminEnabled
    get() = !env("ADMIN_ENABLED").isNullOrEmpty()

val Application.isAdminAvailable
    get() = isAdminEnabled && dependencies.contains<AdminAuthService>()

fun DependencyRegistry.provideAdminAuthService() {
    val keyFactory = KeyFactory.getInstance("RSA")
    val publicKey = keyFactory.generatePublic(
        X509EncodedKeySpec(
            Base64.getDecoder().decode(requireEnvNonBlank("ADMIN_PUBLIC_KEY"))
        )
    )

    val rsa = publicKey as? RSAPublicKey
        ?: throw IllegalStateException("ADMIN_PUBLIC_KEY is not an RSA public key (got ${publicKey.algorithm})")

    val bits = rsa.modulus.bitLength()
    if (bits < 2048) {
        logger.warn("Admin RSA public key is $bits bits (< 2048). Not recommended.")
    }

    val allowedIPs = env("ADMIN_ALLOWED_IPS").let { ipString ->
        val ips = ipString.let {
            if (it.isNullOrBlank()) {
                logger.warn("ADMIN_ALLOWED_IPS is not set, only allowing admin access from local machine.")
                return@let "127.0.0.0/8,::1/128"
            }

            return@let it
        }

        if (ips == "*") {
            logger.warn("ADMIN_ALLOWED_IPS is set to '*', allowing access from any IP address. Not recommended!")
            null
        } else ips.split(",").map { CIDR.parse(it) }
    }

    provide<AdminAuthService> {
        AdminAuthServiceImpl(
            AdminAuthServiceImpl.Config(
                sessionDurationSeconds =
                    (env("ADMIN_SESSION_DURATION")?.toIntOrNull()?.seconds ?: 1.hours).inWholeSeconds,
                minimumSessionCreationTime =
                    (env("ADMIN_SESSION_CREATION_MINIMUM_TIME")?.toIntOrNull()?.milliseconds
                        ?: 3.seconds),
                allowedIPs = allowedIPs,
                publicKey = publicKey,
                challengeTimeoutSeconds =
                    (env("ADMIN_CHALLENGE_TIMEOUT")?.toIntOrNull()?.seconds ?: 1.minutes).inWholeSeconds
            )
        )
    }
}

class AdminAuthServiceImpl(val config: Config) : AdminAuthService {
    private class SessionData(
        val token: String,
        val expires: LocalDateTime,
    )

    private class Challenge(
        val bytes: ByteArray,
        val expires: LocalDateTime,
    )

    companion object {
        private const val CHALLENGE_SIZE = 128
        private const val TOKEN_SIZE = 64
        private val logger = LoggerFactory.getLogger(AdminAuthServiceImpl::class.java)
        private val secureRand = SecureRandom()
    }

    private val currentChallenge: AtomicReference<Challenge?> = AtomicReference(null)
    private val sessionData: AtomicReference<SessionData?> = AtomicReference(null)

    class Config(
        val sessionDurationSeconds: Long,
        val minimumSessionCreationTime: Duration,
        val publicKey: PublicKey,
        val allowedIPs: List<CIDR>?,
        val challengeTimeoutSeconds: Long,
    )

    init {
        logger.warn("AdminAuthService is running!")
    }

    private fun permitsIP(ip: String) =
        config.allowedIPs?.let { ip in it } ?: true

    private fun verifySignature(signature: String, challenge: ByteArray): Boolean {
        return try {
            val sigBytes = Base64.getUrlDecoder().decode(signature)

            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(config.publicKey)
            sig.update(challenge)

            sig.verify(sigBytes)
        } catch (e: SignatureException) {
            logger.error("Error verifying signature", e)
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    override fun newChallenge(): String {
        val challengeBytes = ByteArray(CHALLENGE_SIZE).apply { secureRand.nextBytes(this) }
        val challenge = Challenge(
            bytes = challengeBytes,
            expires = LocalDateTime.now().plusSeconds(config.challengeTimeoutSeconds),
        )

        currentChallenge.set(challenge)

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(challengeBytes)
    }

    override suspend fun createSession(signature: String, ip: String): CreateSessionResult {
        if (!permitsIP(ip)) {
            return CreateSessionResult.IPNotAllowed(ip)
        }

        return withMinimumDelay(config.minimumSessionCreationTime) {
            val challenge = currentChallenge.getAndSet(null)
            if (challenge == null || challenge.expires.isBefore(LocalDateTime.now()))
                return@withMinimumDelay CreateSessionResult.NoChallenge

            if (verifySignature(signature, challenge.bytes)) {
                val session = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(ByteArray(TOKEN_SIZE).apply { secureRand.nextBytes(this) })

                val data = SessionData(
                    token = session,
                    expires = LocalDateTime.now().plusSeconds(config.sessionDurationSeconds),
                )

                sessionData.set(data)

                logger.info("New admin session created (IP: $ip)")

                CreateSessionResult.Success(session)
            } else {
                CreateSessionResult.InvalidSignature
            }
        }
    }

    override fun hasSession(token: String, ip: String): Boolean {
        if (!permitsIP(ip)) {
            return false
        }

        val session = sessionData.get()
        session ?: return false

        if (session.expires.isBefore(LocalDateTime.now())) {
            return false
        }

        return MessageDigest.isEqual(session.token.toByteArray(), token.toByteArray())
    }

    override fun clearSession() {
        sessionData.set(null)
    }
}