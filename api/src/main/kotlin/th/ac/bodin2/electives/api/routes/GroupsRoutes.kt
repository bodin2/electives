package th.ac.bodin2.electives.api.routes

import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.routing
import kotlinx.serialization.SerialName
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.ADMIN_AUTHENTICATION
import th.ac.bodin2.electives.api.RATE_LIMIT_ADMIN
import th.ac.bodin2.electives.api.RATE_LIMIT_USERS
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.isAdminEnabled
import th.ac.bodin2.electives.api.services.GroupService
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.*
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.proto.api.AdminService
import th.ac.bodin2.electives.proto.api.Group as GroupProto
import th.ac.bodin2.electives.proto.api.UserType

val groupsController = controller {
    val groupService: GroupService by dependencies

    routing {
        authenticatedRoutes {
            rateLimit(RATE_LIMIT_USERS) {
                get<Groups> {
                    authenticated(ELEVATED_USER_ONLY) { user ->
                        handleGetGroups(groupService, user)
                    }
                }

                get<Groups.MemberCounts> {
                    authenticated(ELEVATED_USER_ONLY) { user ->
                        handleGetGroupMemberCounts(groupService, user)
                    }
                }

                get<Groups.Id> { params ->
                    authenticated(ELEVATED_USER_ONLY) { user ->
                        handleGetGroup(groupService, user, params.id)
                    }
                }

                get<Groups.Id.Managers> { params ->
                    authenticated(ELEVATED_USER_ONLY) { user ->
                        handleGetGroupManagers(
                            groupService,
                            user,
                            params.parent.id,
                            params.page,
                            params.query.ifBlank { null })
                    }
                }

                get<Groups.Id.Members> { params ->
                    authenticated(ELEVATED_USER_ONLY) { user ->
                        handleGetGroupMembers(
                            groupService,
                            user,
                            params.parent.id,
                            params.page,
                            params.query.ifBlank { null })
                    }
                }
            }
        }

        if (isAdminEnabled) {
            adminRoutes {
                put<Groups.Id> { params -> handlePutGroup(groupService, params.id) }
                delete<Groups.Id> { params -> handleDeleteGroup(groupService, params.id) }
                patch<Groups.Id> { params -> handlePatchGroup(groupService, params.id) }
                delete<Groups.Id.Members> { params -> handleDeleteGroupMembers(groupService, params.parent.id) }
                post<Groups.Id.Members.Migrate> { params ->
                    handleMigrateGroupMembers(groupService, params.parent.parent.id, params.targetGroupId)
                }
            }
        }
    }
}

/**
 * Returns the set of group ids the current user is allowed to see.
 *
 * Admins return `null`, meaning "no filter — full access". Teachers return the ids of every group they
 * are a manager of via [th.ac.bodin2.electives.db.models.TeacherGroups]. This is the single chokepoint
 * for visibility; every GET handler must consult it.
 */
private suspend fun visibleGroupIds(
    groupService: GroupService,
    user: UsersService.SessionUser,
): Set<Int>? {
    if (user.type == UserType.ADMIN) return null

    @OptIn(Transactional::class)
    return dbQuery { groupService.getTeacherGroups(user.id) }
        .map { it.id.value }
        .toSet()
}

/**
 * Throws [notFound] when the resolved [visible] set is non-null and does not contain [groupId].
 * The 404 hides the existence of groups the teacher cannot see, matching the existing
 * "Group not found" error shape.
 */
private fun requireVisible(visible: Set<Int>?, groupId: Int) {
    if (visible != null && groupId !in visible) {
        throw notFound("Group not found")
    }
}

private suspend fun RoutingContext.handleGetGroups(
    groupService: GroupService,
    user: UsersService.SessionUser,
) {
    val visible = visibleGroupIds(groupService, user)
    val groups = dbQuery {
        groupService.getAll()
            .filter { visible == null || it.id.value in visible }
            .map { it.toProto() }
    }
    call.respond(AdminService.ListGroupsResponse(groups = groups))
}

private suspend fun RoutingContext.handleGetGroup(
    groupService: GroupService,
    user: UsersService.SessionUser,
    id: Int,
) {
    val visible = visibleGroupIds(groupService, user)
    requireVisible(visible, id)

    val response = dbQuery { groupService.getById(id)?.toProto() }
        ?: throw notFound()

    call.respond(response)
}

private suspend fun RoutingContext.handleGetGroupManagers(
    groupService: GroupService,
    user: UsersService.SessionUser,
    groupId: Int,
    page: Int,
    query: String?,
) {
    val visible = visibleGroupIds(groupService, user)
    requireVisible(visible, groupId)

    val (users, total) = dbQuery {
        val (teachers, count) =
            @OptIn(Transactional::class)
            groupService.getManagers(groupId, page, query)

        teachers.map { it.toProto() } to count.toInt()
    }

    call.respond(AdminService.ListUsersResponse(users = users, total = total))
}

private suspend fun RoutingContext.handleGetGroupMembers(
    groupService: GroupService,
    user: UsersService.SessionUser,
    groupId: Int,
    page: Int,
    query: String?,
) {
    val visible = visibleGroupIds(groupService, user)
    requireVisible(visible, groupId)

    val response = dbQuery {
        val (members, count) =
            @OptIn(Transactional::class) groupService.getMembers(groupId, page, query)

        AdminService.ListUsersResponse(
            users = members.map { it.toProto() },
            total = count.toInt(),
        )
    }

    call.respond(response)
}

private suspend fun RoutingContext.handleGetGroupMemberCounts(
    groupService: GroupService,
    user: UsersService.SessionUser,
) {
    val visible = visibleGroupIds(groupService, user)
    val counts = dbQuery { groupService.getMemberCounts() }
        .let { if (visible == null) it else it.filterKeys { id -> id in visible } }

    call.respond(AdminService.GroupMemberCounts(member_counts = counts))
}

private suspend fun RoutingContext.handlePutGroup(
    groupService: GroupService,
    id: Int,
) {
    val group = call.parseOrNull<GroupProto>()
        ?: throw badRequest()

    if (group.id != id) throw badRequest("ID in URL does not match body")

    try {
        @OptIn(Transactional::class)
        groupService.create(group.id, group.name, group.type, group.parent_id)
    } catch (_: ConflictException) {
        throw conflict("Group with the same ID already exists")
    } catch (e: ExposedSQLException) {
        throw badRequest(e.message ?: "SQL exception occurred")
    }
    noContent()
}

private suspend fun RoutingContext.handleDeleteGroup(
    groupService: GroupService,
    id: Int,
) {
    try {
        @OptIn(Transactional::class)
        groupService.delete(id)
    } catch (_: ConflictException) {
        throw conflict("Group has members; reassign or remove them before deleting")
    } catch (e: ExposedSQLException) {
        throw badRequest(e.message ?: "SQL exception occurred")
    }
    noContent()
}

private suspend fun RoutingContext.handlePatchGroup(
    groupService: GroupService,
    id: Int,
) {
    val req = call.parseOrNull<AdminService.GroupPatch>()
        ?: throw badRequest()

    val update = GroupService.GroupUpdate(
        name = req.name,
        parentId = req.parent_id,
        setParentId = req.patch_parent_id,
    )

    val proto = try {
        dbQuery {
            @OptIn(Transactional::class)
            groupService.update(id, update).toProto()
        }
    } catch (_: NothingToUpdateException) {
        throw badRequest("Nothing to update")
    }
    call.respond(proto)
}

private suspend fun RoutingContext.handleDeleteGroupMembers(
    groupService: GroupService,
    groupId: Int,
) {
    @OptIn(Transactional::class)
    dbQuery {
        groupService.deleteMembers(groupId)
    }

    noContent()
}

private suspend fun RoutingContext.handleMigrateGroupMembers(
    groupService: GroupService,
    groupId: Int,
    targetGroupId: Int,
) {
    try {
        @OptIn(Transactional::class)
        dbQuery {
            groupService.migrateMembers(groupId, targetGroupId)
        }
    } catch (_: ConflictException) {
        throw conflict("Target group must be a different group of the same type")
    }

    ok()
}

private fun Application.adminRoutes(block: Route.() -> Unit) {
    routing {
        authenticate(ADMIN_AUTHENTICATION) {
            rateLimit(RATE_LIMIT_ADMIN) {
                block()
            }
        }
    }
}

@Suppress("UNUSED")
@Resource("/groups")
private class Groups {
    // PUT: Group, GET: Group, DELETE, PATCH: GroupPatch
    @Resource("{id}")
    class Id(val parent: Groups = Groups(), val id: Int) {
        // GET: ListUsersResponse
        @Resource("managers")
        class Managers(val parent: Id, val page: Int = 1, val query: String = "")

        // GET: ListUsersResponse, DELETE
        @Resource("members")
        class Members(val parent: Id, val page: Int = 1, val query: String = "") {
            // POST
            @Resource("migrate")
            class Migrate(val parent: Members, @SerialName("target_group_id") val targetGroupId: Int)
        }
    }

    // GET: GroupMemberCounts
    @Resource("member-counts")
    class MemberCounts(val parent: Groups = Groups())
}
