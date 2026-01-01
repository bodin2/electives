package th.ac.bodin2.electives.api

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.di.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.api.routes.*
import th.ac.bodin2.electives.api.services.*
import th.ac.bodin2.electives.db.Database
import th.ac.bodin2.electives.utils.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal val logger: Logger = LoggerFactory.getLogger("ElectivesAPI")

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
            Database.init("jdbc:sqlite:$path", "org.sqlite.JDBC")
        } else {
            logger.warn("Database already initialized? If you're running this in production, this is not normal.")
        }
    }

    configureHTTP()
    configureSecurity()

    registerAuthRoutes()
    registerElectivesRoutes()
    registerUsersRoutes()
    registerMiscRoutes()
    registerNotificationsRoutes()
}

fun Application.provideDependencies() {
    dependencies {
        provide<UsersService> {
            UsersServiceImpl(
                UsersServiceImpl.Config(
                    sessionDurationSeconds =
                        (getEnv("USER_SESSION_DURATION")?.toIntOrNull()?.seconds ?: 1.days).inWholeSeconds
                )
            )
        }
        provide<NotificationsService> {
            NotificationsServiceImpl(
                NotificationsServiceImpl.Config(
                    maxSubjectSubscriptionsPerClient =
                        getEnv("NOTIFICATIONS_UPDATE_MAX_SUBSCRIPTIONS_PER_CLIENT")?.toIntOrNull() ?: 5,
                    bulkUpdateInterval =
                        getEnv("NOTIFICATIONS_BULK_UPDATE_INTERVAL")?.toIntOrNull()?.milliseconds ?: 5.seconds,
                    bulkUpdatesEnabled = true
                )
            )
        }
        provide<ElectiveService> { ElectiveServiceImpl() }
        provide<ElectiveSelectionService> {
            val notificationsService = resolve<NotificationsService>()
            val usersService = resolve<UsersService>()
            ElectiveSelectionServiceImpl(usersService, notificationsService)
        }
    }
}