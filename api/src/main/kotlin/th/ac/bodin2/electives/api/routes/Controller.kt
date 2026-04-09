package th.ac.bodin2.electives.api.routes

import io.ktor.server.application.*

interface Controller {
    /**
     * Register routes for this controller
     */
    fun Application.register()
}

fun controller(register: Application.() -> Unit): Controller = object : Controller {
    override fun Application.register() = register()
}