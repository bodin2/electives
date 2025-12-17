package th.ac.bodin2.electives.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.bodylimit.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import th.ac.bodin2.electives.utils.*

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
         logger.info("IS_BEHIND_PROXY is set, respecting forwarded headers. This may be dangerous.")

         install(ForwardedHeaders)
         install(XForwardedHeaders)
    }
}
