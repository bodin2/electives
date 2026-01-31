package th.ac.bodin2.electives.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.bodylimit.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.authenticatedUserId
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.utils.KiB
import th.ac.bodin2.electives.utils.getEnv
import th.ac.bodin2.electives.utils.requireEnvNonBlank
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Application.configureHTTP() {
    install(Resources)

    install(RequestBodyLimit) {
        bodyLimit { call ->
            when (val path = call.request.path()) {
                "/auth" -> 2.KiB
                "/notifications" -> Long.MAX_VALUE
                else -> {
                    if (path.startsWith("/users")) 1.KiB
                    else 0
                }
            }
        }
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)

        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)

        exposedHeaders += listOf("X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset")
        exposedHeaders += HttpHeaders.RetryAfter

        if (isDev || isTest) {
            anyHost()
        } else {
            val hosts = requireEnvNonBlank("CORS_HOSTS")

            hosts.split(",").forEach { host ->
                allowHost(host.trim(), schemes = listOf("https"))
            }
        }
    }

    install(ConditionalHeaders)

    if (!getEnv("IS_BEHIND_PROXY").isNullOrBlank()) {
        logger.info("IS_BEHIND_PROXY is set, respecting forwarded headers.")

        install(ForwardedHeaders)
        install(XForwardedHeaders)
    }

    if (!isTest) {
        configureRateLimits()
    }
}

private fun Application.configureRateLimits() {
    val usersService: UsersService by dependencies

    install(RateLimit) {
        val authenticated: suspend (ApplicationCall) -> Any = { it.authenticatedUserId() ?: Unit }

        register(RATE_LIMIT_AUTH) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { it.request.origin.remoteAddress }
        }

        register(RATE_LIMIT_ELECTIVES) {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
            requestKey { it.authenticatedUserId() ?: it.request.origin.remoteAddress }
        }

        register(RATE_LIMIT_ELECTIVES_SUBJECT_MEMBERS) {
            // Really expensive operation, so lower limit
            rateLimiter(limit = 15, refillPeriod = 1.minutes)
            requestKey(authenticated)
        }

        register(RATE_LIMIT_NOTIFICATIONS) {
            // 5 connection attempts every 10 seconds
            rateLimiter(limit = 5, refillPeriod = 10.seconds)
            requestKey(authenticated)
        }

        register(RATE_LIMIT_USERS) {
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey(authenticated)
        }

        register(RATE_LIMIT_USERS_SELECTIONS) {
            // Prevent spammy requests
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
            requestKey(authenticated)
            requestWeight { _, key ->
                if (key is Int)
                    return@requestWeight when (transaction { usersService.getUserType(key) }) {
                        // Teachers are not affected by elective selection limits
                        UserType.TEACHER -> 0
                        else -> 1
                    }

                return@requestWeight 1
            }
        }
    }
}

val RATE_LIMIT_AUTH = RateLimitName("auth")
val RATE_LIMIT_ELECTIVES = RateLimitName("electives")
val RATE_LIMIT_ELECTIVES_SUBJECT_MEMBERS = RateLimitName("electives.subject.members")
val RATE_LIMIT_NOTIFICATIONS = RateLimitName("notifications")
val RATE_LIMIT_USERS = RateLimitName("users")
val RATE_LIMIT_USERS_SELECTIONS = RateLimitName("users.selections")