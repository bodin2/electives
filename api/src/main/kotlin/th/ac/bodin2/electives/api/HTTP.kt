package th.ac.bodin2.electives.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.bodylimit.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.resources.*
import th.ac.bodin2.electives.utils.getEnv
import th.ac.bodin2.electives.utils.isDev
import th.ac.bodin2.electives.utils.isTest

fun Application.configureHTTP() {
    install(Resources)

    install(RequestBodyLimit) {
        bodyLimit { 4 * 1024 * 1024L } // 4 MiB
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)

        if (isDev || isTest) {
            anyHost()
        } else {
            val hosts = getEnv("CORS_HOSTS")
            if (hosts.isNullOrBlank()) throw Exception("CORS_HOSTS is not set in production mode")

            hosts.split(",").forEach { host ->
                allowHost(host.trim(), schemes = listOf("https"))
            }
        }
    }

    install(ConditionalHeaders)

    // install(ForwardedHeaders) // @TODO: WARNING: for security, do not include this if not behind a reverse proxy
    // install(XForwardedHeaders) // @TODO: WARNING: for security, do not include this if not behind a reverse proxy
}
