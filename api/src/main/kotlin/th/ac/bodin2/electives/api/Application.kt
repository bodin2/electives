package th.ac.bodin2.electives.api

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.api.routes.*
import th.ac.bodin2.electives.db.Database
import th.ac.bodin2.electives.utils.getEnv
import th.ac.bodin2.electives.utils.isDev
import th.ac.bodin2.electives.utils.loadDotEnv
import java.security.Security

private val logger: Logger = LoggerFactory.getLogger("ElectivesAPI")

fun main() {
    // Required for PASETO support (loading keys)
    Security.addProvider(BouncyCastleProvider())

    if (isDev) {
        logger.warn("Running in development mode!")
        loadDotEnv()
    }

    embeddedServer(
        CIO,
        configure = {
            connectionIdleTimeoutSeconds = 45
            connector {
                host = getEnv("HOST") ?: "0.0.0.0"
                port = getEnv("PORT")?.toInt() ?: 8080
            }
        },
        module = Application::module
    ).start(wait = true)

    logger.info("Shutting down!")
}

fun Application.module() {
    Database.init()

    configureHTTP()
    configureSecurity()

    registerAuthRoutes()
    registerElectivesRoutes()
    registerUsersRoutes()
    registerMiscRoutes()
    registerNotificationsRoutes()

    if (isDev) {
        registerDevRoutes()
    }
}
