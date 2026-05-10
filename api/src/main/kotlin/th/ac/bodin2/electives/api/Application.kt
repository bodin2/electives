package th.ac.bodin2.electives.api

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.di.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
        enrollmentsController,
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
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = requireEnvNonBlank("DB_URL")
            username = env("DB_USER")
            password = env("DB_PASSWORD")
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = env("DB_POOL_SIZE")?.toInt() ?: 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        })
        Database.init(dataSource)
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

    provide<EnrollmentService> { EnrollmentServiceImpl() }
    provide<SubjectService> { SubjectServiceImpl() }
    provide<GroupService> { GroupServiceImpl() }
    provide<EnrollmentSelectionService> { EnrollmentSelectionServiceImpl(resolve<NotificationsService>()) }

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