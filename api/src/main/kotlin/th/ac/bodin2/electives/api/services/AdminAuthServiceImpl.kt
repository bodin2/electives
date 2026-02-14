package th.ac.bodin2.electives.api.services

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.api.services.AdminAuthService.CreateSessionResult
import th.ac.bodin2.electives.utils.Argon2
import th.ac.bodin2.electives.utils.CIDR
import th.ac.bodin2.electives.utils.contains
import th.ac.bodin2.electives.utils.withMinimumDelay
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.SignatureException
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

class AdminAuthServiceImpl(val config: Config) : AdminAuthService {
    private class SessionData(
        val hash: String,
        val expires: LocalDateTime,
    )

    companion object {
        private const val CHALLENGE_SIZE = 128
        private const val TOKEN_SIZE = 64
        private val logger = LoggerFactory.getLogger(AdminAuthServiceImpl::class.java)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val currentChallenge: AtomicReference<ByteArray?> = AtomicReference(null)
    private val sessionData: AtomicReference<SessionData> = AtomicReference(null)

    class Config(
        val sessionDurationSeconds: Long,
        val minimumSessionCreationTime: Duration,
        val publicKey: PublicKey,
        val allowedIPs: List<CIDR>?,
        val challengeTimeoutMillis: Long,
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
        val challenge = ByteArray(CHALLENGE_SIZE).apply {
            SecureRandom().nextBytes(this)
        }

        currentChallenge.set(challenge)

        // Clear the challenge after duration
        scope.launch {
            delay(config.challengeTimeoutMillis)
            currentChallenge.compareAndSet(challenge, null)
        }

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(challenge)
    }

    override suspend fun createSession(signature: String, ip: String): CreateSessionResult {
        if (!permitsIP(ip)) {
            return CreateSessionResult.IPNotAllowed(ip)
        }

        return withMinimumDelay(config.minimumSessionCreationTime) {
            val challenge = currentChallenge.get()
            challenge ?: return@withMinimumDelay CreateSessionResult.NoChallenge

            if (verifySignature(signature, challenge)) {
                val session = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(ByteArray(TOKEN_SIZE).apply {
                        SecureRandom().nextBytes(this)
                    })

                val data = SessionData(
                    hash = Argon2.hash(session.toCharArray()),
                    expires = LocalDateTime.now().plusSeconds(config.sessionDurationSeconds),
                )

                sessionData.set(data)
                currentChallenge.set(null)

                logger.warn("New admin session created (IP: $ip)")

                CreateSessionResult.Success(session)
            } else {
                currentChallenge.set(null)
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

        return Argon2.verify(session.hash.toByteArray(), token.toCharArray())
    }

    override fun clearSession() {
        sessionData.set(null)
    }
}