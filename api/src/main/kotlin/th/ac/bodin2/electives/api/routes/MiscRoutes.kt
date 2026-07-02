package th.ac.bodin2.electives.api.routes

import io.ktor.server.routing.*
import th.ac.bodin2.electives.api.utils.ok

val miscController = controller {
    routing {
        head("/status") { ok() }
    }
}