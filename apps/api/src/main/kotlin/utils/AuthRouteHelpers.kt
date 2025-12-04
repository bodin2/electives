package th.ac.bodin2.electives.api.utils

import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import th.ac.bodin2.electives.api.USER_AUTHENTICATION
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.proto.api.UserType

val TEACHER_USER_ONLY = listOf(UserType.TEACHER)
val ALL_USER_TYPES = UserType.entries.toList()

/**
 * Helper function to get the authenticated user ID from the routing context.
 * If the user is not authenticated, it responds with HTTP 401 Unauthorized.
 *
 * @param block A lambda function that takes the authenticated user ID as a parameter.
 * @param types List of user types able to access this resource. Default is any type.
 */
suspend fun RoutingContext.authenticated(types: List<UserType> = ALL_USER_TYPES, block: suspend RoutingContext.(userId: Int) -> Unit) {
    val userId = call.principal<Int>()
        ?: return unauthorized()

    if (UsersService.getUserType(userId) !in types) {
        return unauthorized()
    }

    block(userId)
}

fun Routing.authenticated(types: List<UserType> = ALL_USER_TYPES, block: Route.() -> Unit) {
    authenticate(USER_AUTHENTICATION) {
        block()
    }
}