package th.ac.bodin2.electives.api.utils

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import th.ac.bodin2.electives.api.AdminPrincipal
import th.ac.bodin2.electives.api.USER_AUTHENTICATION
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.proto.api.UserType

val ELEVATED_USER_ONLY = listOf(UserType.TEACHER, UserType.ADMIN)
val ALL_USER_TYPES = UserType.entries.toList()

/**
 * Helper function to get the authenticated user ID from the routing context.
 * If the user is not authenticated, it responds with HTTP 401 Unauthorized.
 *
 * @param block A lambda function that takes the authenticated user ID as a parameter.
 * @param types List of user types able to access this resource. Default is any type.
 */
suspend fun RoutingContext.authenticated(
    types: List<UserType> = ALL_USER_TYPES,
    block: suspend RoutingContext.(user: UsersService.SessionUser) -> Unit
) {
    val user = call.user ?: return unauthorized()
    if (user.type !in types) return unauthorized()

    block(user)
}

/**
 * Helper function to get the authenticated user ID from the routing context with dynamic user type checking.
 * If the user is not authenticated or does not have the required type, it responds with HTTP 401 Unauthorized.
 *
 * @param getTypes A lambda function that takes the authenticated user ID and returns a list of allowed user types.
 * @param block A lambda function that takes the authenticated user ID as a parameter.
 */
suspend fun RoutingContext.authenticated(
    getTypes: (userId: Int) -> List<UserType>,
    block: suspend RoutingContext.(user: UsersService.SessionUser) -> Unit
) {
    val user = call.user ?: return unauthorized()
    if (user.type !in getTypes(user.id)) return unauthorized()

    block(user)
}

// NOTE TO SELF: DO NOT CHANGE TO Routing.() -> Unit
// This will break authentication silently. You will have a bad time debugging it.
fun Routing.authenticatedRoutes(block: Route.() -> Unit) {
    authenticate(USER_AUTHENTICATION) {
        block()
    }
}

val ApplicationCall.user get() = principal<UsersService.SessionUser>()

fun ApplicationCall.userId(): Int? {
    return principal<UsersService.SessionUser>()?.id
}

fun ApplicationCall.isAdmin(): Boolean = principal<AdminPrincipal>() != null