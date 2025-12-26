package th.ac.bodin2.electives.api

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.di.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.api.routes.*
import th.ac.bodin2.electives.api.services.*
import th.ac.bodin2.electives.db.Database
import th.ac.bodin2.electives.utils.getEnv
import th.ac.bodin2.electives.utils.isDev
import th.ac.bodin2.electives.utils.isTest
import th.ac.bodin2.electives.utils.loadDotEnv

internal val logger: Logger = LoggerFactory.getLogger("ElectivesAPI")

fun main() {
    if (isTest) {
        logger.warn("Running in test mode!")
    }

    if (isDev) {
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
//    @TODO: Ratelimits

    if (!isTest) {
        dependencies {
            provide<UsersService> { UsersServiceImpl() }
            provide<NotificationsService> { NotificationsServiceImpl() }
            provide<ElectiveService> { ElectiveServiceImpl() }
            provide<ElectiveSelectionService> {
                val notificationsService = resolve<NotificationsService>()
                val usersService = resolve<UsersService>()
                ElectiveSelectionServiceImpl(usersService, notificationsService)
            }
        }
    }

    if (!TransactionManager.isInitialized()) {
        val path = getEnv("DB_PATH") ?: ""
        val defaultPath = "data.db"

        Database.init(
            "jdbc:sqlite:${
                path.ifBlank {
                    logger.warn("DB_PATH not specified, using default path: $defaultPath")
                    defaultPath
                }
            }", "org.sqlite.JDBC"
        )
    } else if (!isTest) {
        // Already initialized in non-test environment?
        logger.warn("Database already initialized? If you're running this in production, this is not normal.")
    }

    configureHTTP()
    configureSecurity()

    registerAuthRoutes()
    registerElectivesRoutes()
    registerUsersRoutes()
    registerMiscRoutes()
    registerNotificationsRoutes()
}
