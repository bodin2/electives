package th.ac.bodin2.electives.api

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.di.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.sqlite.jdbc4.JDBC4Connection
import th.ac.bodin2.electives.api.routes.*
import th.ac.bodin2.electives.api.services.*
import th.ac.bodin2.electives.api.utils.authenticatedUserId
import th.ac.bodin2.electives.db.Database
import th.ac.bodin2.electives.utils.CIDR
import th.ac.bodin2.electives.utils.getEnv
import th.ac.bodin2.electives.utils.loadDotEnv
import th.ac.bodin2.electives.utils.requireEnvNonBlank
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal val logger: Logger = LoggerFactory.getLogger("ElectivesAPI")
internal val callLogger: Logger = LoggerFactory.getLogger("CallLogging")

fun main() {
    if (isTest) {
        logger.warn("Running in test mode!")
    }

    if (isDev) {
        System.setProperty("io.ktor.development", "true")
        logger.warn("Running in development mode!")
        loadDotEnv()
    }

    if (isTest || isDev) {
        logger.warn("Running in non-production mode! Security may be significantly reduced and features may not work as expected!")
    }

    val server = embeddedServer(
        CIO,
        host = getEnv("HOST") ?: "0.0.0.0",
        port = getEnv("PORT")?.toInt() ?: 8080,
        module = Application::module
    )

    server.monitor.subscribe(ApplicationStopping) {
        logger.info("Shutting down!")
    }

    server.start(wait = true)
}

fun Application.module() {
    if (!isTest) {
        provideDependencies()

        if (TransactionManager.primaryDatabase == null) {
            val path = requireEnvNonBlank("DB_PATH")
            Database.init("jdbc:sqlite:$path", "org.sqlite.JDBC") {
                val conn = connector().connection as JDBC4Connection
                conn.createStatement().use {
                    it.execute("PRAGMA foreign_keys=ON;")
                    it.execute("PRAGMA journal_mode=WAL;")
                    it.execute("PRAGMA synchronous=NORMAL;")
                    it.execute("PRAGMA busy_timeout=5000;")
                }
            }
        } else {
            logger.warn("Database already initialized? If you're running this in production, this is not normal.")
        }
    }

    install(CallLogging) {
        logger = callLogger

        mdc("ip") { it.request.origin.remoteAddress }
        mdc("userAgent") { it.request.headers["User-Agent"] }
        mdc("userId") { it.authenticatedUserId()?.toString() }
    }

    configureHTTP()
    configureSecurity()

    registerAuthRoutes()
    registerElectivesRoutes()
    registerUsersRoutes()
    registerMiscRoutes()
    registerNotificationsRoutes()
    if (dependencies.contains(DependencyKey<AdminAuthService>())) registerAdminRoutes()
}

fun Application.provideDependencies() {
    dependencies {
        provide<UsersService> {
            UsersServiceImpl(
                UsersServiceImpl.Config(
                    sessionDurationSeconds =
                        (getEnv("USER_SESSION_DURATION")?.toIntOrNull()?.seconds ?: 1.days).inWholeSeconds,
                    minimumSessionCreationTime =
                        (getEnv("USER_SESSION_CREATION_MINIMUM_TIME")?.toIntOrNull()?.milliseconds ?: 500.milliseconds)
                )
            )
        }

        if (!contains(DependencyKey<NotificationsService>()))
            provide<NotificationsService> {
                NotificationsServiceImpl(
                    NotificationsServiceImpl.Config(
                        maxSubjectSubscriptionsPerClient =
                            getEnv("NOTIFICATIONS_UPDATE_MAX_SUBSCRIPTIONS_PER_CLIENT")?.toIntOrNull() ?: 5,
                        bulkUpdateInterval =
                            getEnv("NOTIFICATIONS_BULK_UPDATE_INTERVAL")?.toIntOrNull()?.milliseconds ?: 5.seconds,
                        bulkUpdatesEnabled = true
                    ),
                    resolve<UsersService>(),
                    DependencyKey<AdminAuthService>().let {
                        if (contains(it)) get(it) else null
                    }
                )
            }

        provide<ElectiveService> { ElectiveServiceImpl() }
        provide<SubjectService> { SubjectServiceImpl() }
        provide<TeamService> { TeamServiceImpl() }
        provide<ElectiveSelectionService> {
            ElectiveSelectionServiceImpl(
                resolve<UsersService>(),
                resolve<NotificationsService>()
            )
        }

        if (!getEnv("ADMIN_ENABLED").isNullOrEmpty() && !contains(DependencyKey<AdminAuthService>())) {
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

            provide<AdminAuthService> {
                AdminAuthServiceImpl(
                    AdminAuthServiceImpl.Config(
                        sessionDurationSeconds = 1.hours.inWholeSeconds,
                        minimumSessionCreationTime = 3.seconds,
                        allowedIPs = getEnv("ADMIN_ALLOWED_IPS").let { ipString ->
                            val ips = ipString.let {
                                if (it.isNullOrBlank()) {
                                    logger.warn("ADMIN_ALLOWED_IPS is not set, defaulting to only allow localhost access.")
                                    return@let "127.0.0.0/8,::1/128"
                                }

                                return@let it
                            }

                            if (ips == "*") {
                                logger.warn("ADMIN_ALLOWED_IPS is set to '*', allowing access from any IP address. Not recommended!")
                                null 
                            } else ips.split(",").map { CIDR.parse(it.trim()) }
                        },
                        publicKey = publicKey,
                        challengeTimeoutSeconds = 60
                    )
                )
            }
        }
    }
}