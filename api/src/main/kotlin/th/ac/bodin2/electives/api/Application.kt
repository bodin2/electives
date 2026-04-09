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
import th.ac.bodin2.electives.api.utils.userId
import th.ac.bodin2.electives.db.Database
import th.ac.bodin2.electives.utils.Argon2
import th.ac.bodin2.electives.utils.env
import th.ac.bodin2.electives.utils.loadDotEnv
import th.ac.bodin2.electives.utils.requireEnvNonBlank

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
        host = env("HOST") ?: "0.0.0.0",
        port = env("PORT")?.toInt() ?: 8080,
        module = Application::module,
        watchPaths = listOf("classes", "resources"),
    )

    server.monitor.subscribe(ApplicationStopping) {
        logger.info("Shutting down!")
    }

    server.start(wait = true)
}

suspend fun Application.module() {
    if (!isTest) {
        provideDependencies()
        setupDatabase()
    }

    install(CallLogging) {
        logger = callLogger

        mdc("ip") { it.request.origin.remoteAddress }
        mdc("userAgent") { it.request.headers["User-Agent"] }
        mdc("userId") { it.userId()?.toString() }
    }

    configureHTTP()
    configureWebSocket()
    configureSecurity()

    val controllers = mutableListOf(
        authController,
        electivesController,
        miscController,
        notificationsController,
        usersController,
    )

    if (isAdminAvailable) {
        controllers += adminController
    }

    for (ctl in controllers) ctl.apply { this@module.register() }
}

fun setupDatabase() {
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
        logger.warn("Database already initialized? This is not normal.")
    }
}

fun Application.provideDependencies() = dependencies {
    if (!contains<Argon2>()) {
        provideArgon2()
    }

    provideUsersService()
    if (!contains<NotificationsService>()) {
        provideNotificationsService()
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

    if (isAdminEnabled) {
        provideAdminAuthService()
    }
}

inline fun <reified T : Any> DependencyRegistry.contains() =
    contains(DependencyKey<T>())

suspend inline fun <reified T : Any> DependencyRegistry.resolveOrNull(): T? {
    val key = DependencyKey<T>()
    return if (contains(key)) get(key) else null
}