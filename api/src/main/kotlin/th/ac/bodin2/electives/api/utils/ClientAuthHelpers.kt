package th.ac.bodin2.electives.api.utils

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import org.jetbrains.exposed.sql.transactions.transaction
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
suspend fun RoutingContext.authenticated(
    types: List<UserType> = ALL_USER_TYPES,
    block: suspend RoutingContext.(userId: Int) -> Unit
) {
    val userId = call.authenticatedUserId() ?: return unauthorized()
    val usersService: UsersService by call.application.dependencies
    if (usersService.missingType(userId, types)) return unauthorized()

    block(userId)
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
    block: suspend RoutingContext.(userId: Int) -> Unit
) {
    val userId = call.authenticatedUserId() ?: return unauthorized()
    val usersService: UsersService by call.application.dependencies
    if (usersService.missingType(userId, getTypes(userId))) return unauthorized()

    block(userId)
}

/**
 * [RoutingContext.authenticated], but for WebSocket sessions.
 */
suspend fun WebSocketServerSession.authenticated(
    types: List<UserType> = ALL_USER_TYPES,
    block: suspend WebSocketServerSession.(userId: Int) -> Unit
) {
    val userId = call.authenticatedUserId() ?: return unauthorized()
    val usersService: UsersService by call.application.dependencies
    if (usersService.missingType(userId, types)) return unauthorized()

    block(userId)
}

@Suppress("NOTHING_TO_INLINE")
inline fun UsersService.missingType(userId: Int, types: List<UserType>): Boolean {
    return transaction { this@missingType.getUserType(userId) } !in types
}

// NOTE TO SELF: DO NOT CHANGE TO Routing.() -> Unit
// This will break authentication silently. You will have a bad time debugging it.
fun Routing.authenticatedRoutes(block: Route.() -> Unit) {
    authenticate(USER_AUTHENTICATION) {
        block()
    }
}

fun ApplicationCall.authenticatedUserId(): Int? {
    return principal<Int>()
}