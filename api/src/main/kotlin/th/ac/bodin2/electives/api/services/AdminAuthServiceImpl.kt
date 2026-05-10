package th.ac.bodin2.electives.api.services

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.contains
import th.ac.bodin2.electives.api.logger
import th.ac.bodin2.electives.api.services.AdminAuthService.CreateSessionResult
import th.ac.bodin2.electives.db.models.Admins
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.utils.CIDR
import th.ac.bodin2.electives.utils.contains
import th.ac.bodin2.electives.utils.env
import th.ac.bodin2.electives.utils.withMinimumDelay
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
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

private fun tryGetDefaultPublicKey(): PublicKey? {
    val env = env("ADMIN_PUBLIC_KEY")
    if (env.isNullOrEmpty()) return null

    val keyFactory = KeyFactory.getInstance("RSA")
    val publicKey = keyFactory.generatePublic(
        X509EncodedKeySpec(
            Base64.getDecoder().decode(env)
        )
    )

    val rsa = publicKey as? RSAPublicKey
        ?: throw IllegalStateException("ADMIN_PUBLIC_KEY is not an RSA public key (got ${publicKey.algorithm})")

    val bits = rsa.modulus.bitLength()
    if (bits < 2048) {
        logger.warn("Admin RSA public key is $bits bits (< 2048). Not recommended.")
    }

    return publicKey
}

fun DependencyRegistry.provideAdminAuthService() {
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
            config = AdminAuthServiceImpl.Config(
                minimumSessionCreationTime =
                    (env("ADMIN_SESSION_CREATION_MINIMUM_TIME")?.toIntOrNull()?.milliseconds
                        ?: 3.seconds),
                allowedIPs = allowedIPs,
                challengeTimeoutSeconds =
                    (env("ADMIN_CHALLENGE_TIMEOUT")?.toIntOrNull()?.seconds ?: 1.minutes).inWholeSeconds,
                sessionDurationSeconds =
                    (env("ADMIN_SESSION_DURATION")?.toIntOrNull()?.seconds ?: 1.hours).inWholeSeconds,
                defaultUserPublicKey = tryGetDefaultPublicKey()
            ),
            usersService = resolve<UsersService>(),
        )
    }
}

// @TODO: Test this
private fun isAdminResetEnabled() = !env("ADMIN_RESET").isNullOrBlank()

class AdminAuthServiceImpl(
    val config: Config,
    val usersService: UsersService,
) : AdminAuthService {
    private class Challenge(
        val bytes: ByteArray,
        val expires: LocalDateTime,
    )

    companion object {
        private const val CHALLENGE_SIZE = 128
        private val logger = LoggerFactory.getLogger(AdminAuthServiceImpl::class.java)
    }

    private val currentChallenge: AtomicReference<Challenge?> = AtomicReference(null)

    class Config(
        val sessionDurationSeconds: Long,
        val minimumSessionCreationTime: Duration,
        val allowedIPs: List<CIDR>?,
        val challengeTimeoutSeconds: Long,
        // @TODO: Test this
        val defaultUserId: Int = 0,
        val defaultUserPublicKey: PublicKey? = null,
    )

    init {
        logger.warn("AdminAuthService is running!")

        @OptIn(Transactional::class)
        transaction {
            try {
                val type = usersService.getUserType(config.defaultUserId)
                if (type != UserType.ADMIN) {
                    logger.warn("Default admin user with ID ${config.defaultUserId} is a ${type.name}. You may need to recreate the database.")
                }

                if (isAdminResetEnabled()) {
                    logger.warn("Resetting admin user with ID ${config.defaultUserId}")
                    usersService.deleteUser(config.defaultUserId)
                    throw EntityNotFoundException(
                        ExceptionEntity.USER,
                        "Admin reset, user ${config.defaultUserId} deleted"
                    )
                }
            } catch (_: EntityNotFoundException) {
                if (config.defaultUserPublicKey == null) {
                    logger.warn("Default admin user with ID ${config.defaultUserId} could not be created. ADMIN_PUBLIC_KEY is required during initial setup.")
                    return@transaction
                }

                usersService.createAdmin(
                    UsersService.AdminInsert(
                        user = UsersService.UserData(
                            id = config.defaultUserId,
                            firstName = "Admin",
                            password = "",
                        ),
                        publicKey = config.defaultUserPublicKey
                    )
                )
            }
        }
    }

    override fun permitsIP(ip: String) =
        config.allowedIPs?.let { ip in it } ?: true

    private fun getAdminPublicKey(id: Int): PublicKey? = transaction {
        val publicKeyString = Admins
            .select(Admins.publicKey)
            .where { Admins.user eq id }
            .singleOrNull()
            ?.get(Admins.publicKey)
            ?: return@transaction null

        val publicKey = try {
            val bytes = Base64.getDecoder().decode(publicKeyString)
            KeyFactory
                .getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(bytes))
        } catch (e: Exception) {
            logger.error("Invalid admin public key for admin user: $id", e)
            return@transaction null
        }

        val rsa = publicKey as? RSAPublicKey
            ?: return@transaction null

        val bits = rsa.modulus.bitLength()
        if (bits < 2048) {
            logger.warn("Admin RSA public key is $bits bits (< 2048), admin user: $id")
        }

        return@transaction publicKey
    }

    private fun verifySignature(signature: String, challenge: ByteArray, publicKey: PublicKey): Boolean {
        return try {
            val sigBytes = Base64.getUrlDecoder().decode(signature)

            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(publicKey)
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
        val challengeBytes = ByteArray(CHALLENGE_SIZE).also { java.security.SecureRandom().nextBytes(it) }
        val challenge = Challenge(
            bytes = challengeBytes,
            expires = LocalDateTime.now().plusSeconds(config.challengeTimeoutSeconds),
        )

        currentChallenge.set(challenge)

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(challengeBytes)
    }

    @OptIn(Transactional::class)
    override suspend fun createSession(
        id: Int,
        signature: String,
        aud: String,
        ip: String,
    ): CreateSessionResult {
        if (!permitsIP(ip)) {
            return CreateSessionResult.IPNotAllowed(ip)
        }

        return withMinimumDelay(config.minimumSessionCreationTime) {
            val challenge = currentChallenge.getAndSet(null)
            if (challenge == null || challenge.expires.isBefore(LocalDateTime.now()))
                return@withMinimumDelay CreateSessionResult.NoChallenge

            val adminPublicKey = getAdminPublicKey(id)
                ?: return@withMinimumDelay CreateSessionResult.UserNotAdmin

            if (!verifySignature(signature, challenge.bytes, adminPublicKey)) {
                return@withMinimumDelay CreateSessionResult.InvalidSignature
            }

            val token = transaction {
                usersService.insecurelyCreateSessionWithoutValidation(id, config.sessionDurationSeconds)
            }

            logger.info("New admin session created, user: $id, aud: $aud, ip: $ip")
            CreateSessionResult.Success(token)
        }
    }
}
