package th.ac.bodin2.electives.api.services

import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.utils.CIDR
import th.ac.bodin2.electives.api.services.AdminService.CreateSessionResult
import th.ac.bodin2.electives.utils.contains
import th.ac.bodin2.electives.utils.Argon2
import th.ac.bodin2.electives.utils.withMinimumDelay
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.SignatureException
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDateTime
import java.util.*
import kotlin.concurrent.Volatile
import kotlin.time.Duration

class AdminServiceImpl(val config: Config) : AdminService {
    companion object {
        private const val CHALLENGE_SIZE = 128
        private const val TOKEN_SIZE = 64
        private val logger = LoggerFactory.getLogger(AdminServiceImpl::class.java)
    }

    @Volatile
    private var currentChallenge: ByteArray? = null

    @Volatile
    private var adminSessionHash: String? = null

    @Volatile
    private var adminSessionExpires: LocalDateTime? = null

    class Config(
        val sessionDurationSeconds: Long,
        val minimumSessionCreationTime: Duration,
        val publicKey: X509EncodedKeySpec,
        val allowedIPs: List<CIDR>?
    )

    init {
        newChallenge()
        logger.warn("AdminService is running!")
    }

    private fun permitsIP(ip: String) =
        config.allowedIPs?.let { ip in it } ?: true

    private fun verifySignature(signature: String, challenge: ByteArray): Boolean {
        val sigBytes = Base64.getUrlDecoder().decode(signature)

        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(config.publicKey)

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initVerify(publicKey)
        sig.update(challenge)

        return try {
            sig.verify(sigBytes)
        } catch (e: SignatureException) {
            logger.error("Error verifying signature", e)
            false
        }
    }

    override fun newChallenge(): String {
        currentChallenge = ByteArray(CHALLENGE_SIZE).apply {
            SecureRandom().nextBytes(this)
        }

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(currentChallenge)
    }

    override suspend fun createSession(signature: String, ip: String): CreateSessionResult {
        if (!permitsIP(ip)) {
            return CreateSessionResult.IPNotAllowed(ip)
        }

        return withMinimumDelay(config.minimumSessionCreationTime) {
            currentChallenge?.let {
                if (verifySignature(signature, it)) {
                    val session = Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(ByteArray(TOKEN_SIZE).apply {
                            SecureRandom().nextBytes(this)
                        })

                    adminSessionHash = Argon2.hash(session.toCharArray())
                    adminSessionExpires = LocalDateTime.now().plusSeconds(config.sessionDurationSeconds)
                    currentChallenge = null

                    logger.warn("New admin session created (IP: $ip)")

                    CreateSessionResult.Success(session)
                } else {
                    CreateSessionResult.InvalidSignature
                }
            } ?: CreateSessionResult.NoChallenge
        }
    }

    override fun hasSession(token: String, ip: String): Boolean {
        if (!permitsIP(ip)) {
            return false
        }

        adminSessionHash ?: return false
        adminSessionExpires ?: return false

        if (adminSessionExpires!!.isBefore(LocalDateTime.now())) {
            return false
        }

        return Argon2.verify(adminSessionHash!!.toByteArray(), token.toCharArray())
    }

    override fun clearSession() {
        adminSessionHash = null
        adminSessionExpires = null
    }
}