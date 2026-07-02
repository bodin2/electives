package th.ac.bodin2.electives.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.utils.ClientException
import th.ac.bodin2.electives.api.utils.badRequest
import java.util.UUID

private fun notFoundEntityMessage(cause: EntityNotFoundException) = "${cause.entity.displayName} not found"

fun EntityNotFoundException.asBadRequest(): ClientException = badRequest(notFoundEntityMessage(this))

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ClientException> { call, cause ->
            if (cause.message != null)
                call.respondText(cause.message, status = cause.statusCode)
            else
                call.respond(cause.statusCode)
        }

        exception<EntityNotFoundException> { call, cause ->
            call.respondText(notFoundEntityMessage(cause), status = HttpStatusCode.NotFound)
        }

        exception<ConflictException> { call, cause ->
            call.respondText("${cause.entity.displayName} already exists", status = HttpStatusCode.Conflict)
        }

        exception<NothingToUpdateException> { call, _ ->
            call.respondText("Nothing to update", status = HttpStatusCode.BadRequest)
        }

        exception<Throwable> { call, cause ->
            if (isDev) {
                call.application.log.error("Unhandled exception", cause)
                call.respondText(cause.stackTraceToString(), status = HttpStatusCode.InternalServerError)
            } else {
                val traceId = UUID.randomUUID().toString()
                call.application.log.error("Unhandled exception [traceId=$traceId]", cause)
                call.response.headers.append("X-Trace-Id", traceId)
                call.respondText("Internal Server Error", status = HttpStatusCode.InternalServerError)
            }
        }
    }
}
