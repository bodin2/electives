package th.ac.bodin2.electives.api.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import th.ac.bodin2.electives.api.*
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.services.mock.TestGroupService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ADMIN_TOKEN
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_GROUP_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.PASSWORD
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_GROUP_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.TEACHER_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.proto.api.AdminService
import th.ac.bodin2.electives.proto.api.Group
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(Transactional::class)
class GroupsRoutesTest : ApplicationTest() {
    private val ApplicationTestBuilder.usersService: UsersService
        get() {
            val service: UsersService by application.dependencies
            return service
        }

    private suspend fun ApplicationTestBuilder.teacherToken(): String {
        startApplication()
        return usersService.createSession(TEACHER_ID, PASSWORD, "")
    }

    private suspend fun ApplicationTestBuilder.studentToken(): String {
        startApplication()
        return usersService.createSession(STUDENT_ID, PASSWORD, "")
    }

    private suspend fun HttpClient.adminGet(url: String): HttpResponse =
        getWithAuth(url, ADMIN_TOKEN)

    // -------- Admin path: parity with the previous /admin/groups behavior --------

    @Test
    fun `get groups without auth returns unauthorized`() = runRouteTest {
        client.get("/groups").assertUnauthorized()
    }

    @Test
    fun `get groups list as admin`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/groups")
            .assertOK()
            .parse<AdminService.ListGroupsResponse>()

        assertEquals(TestGroupService.GROUP_IDS.size, response.groups.size)
    }

    @Test
    fun `get group by id as admin`() = runRouteTest {
        startApplication()

        val group = client.adminGet("/groups/$ENROLLMENT_GROUP_ID")
            .assertOK()
            .parse<Group>()

        assertEquals(ENROLLMENT_GROUP_ID, group.id)
    }

    @Test
    fun `get group not found as admin`() = runRouteTest {
        startApplication()

        client.adminGet("/groups/$UNUSED_ID").assertNotFound()
    }

    @Test
    fun `delete group without auth returns unauthorized`() = runRouteTest {
        client.delete("/groups/$ENROLLMENT_GROUP_ID").assertUnauthorized()
    }

    @Test
    fun `get group member counts without auth returns unauthorized`() = runRouteTest {
        client.get("/groups/member-counts").assertUnauthorized()
    }

    @Test
    fun `get group member counts as admin`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/groups/member-counts")
            .assertOK()
            .parse<AdminService.GroupMemberCounts>()

        assertEquals(TestGroupService.GROUP_IDS.size, response.member_counts.size)
        TestGroupService.GROUP_IDS.forEach { groupId ->
            assertEquals(0, response.member_counts[groupId])
        }
    }

    @Test
    fun `get group members without auth returns unauthorized`() = runRouteTest {
        client.get("/groups/$ENROLLMENT_GROUP_ID/members").assertUnauthorized()
    }

    @Test
    fun `get group members as admin`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/groups/$ENROLLMENT_GROUP_ID/members")
            .assertOK()
            .parse<AdminService.ListUsersResponse>()

        assertEquals(0, response.total)
        assertEquals(0, response.users.size)
    }

    @Test
    fun `get group members not found as admin`() = runRouteTest {
        startApplication()

        client.adminGet("/groups/$UNUSED_ID/members").assertNotFound()
    }

    @Test
    fun `patch group without auth returns unauthorized`() = runRouteTest {
        client.patch("/groups/$ENROLLMENT_GROUP_ID").assertUnauthorized()
    }

    @Test
    fun `patch group as admin returns updated group`() = runRouteTest {
        startApplication()

        val group = client.patchProtoWithAuth(
            "/groups/$ENROLLMENT_GROUP_ID",
            AdminService.GroupPatch(name = "New Name", parent_id = 123, patch_parent_id = true),
            ADMIN_TOKEN
        ).assertOK().parse<Group>()

        assertEquals(ENROLLMENT_GROUP_ID, group.id)
        assertEquals(123, group.parent_id)
    }

    @Test
    fun `patch group not found as admin`() = runRouteTest {
        startApplication()

        client.patchProtoWithAuth(
            "/groups/$UNUSED_ID",
            AdminService.GroupPatch(name = "New Name"),
            ADMIN_TOKEN
        ).assertNotFound("Group not found")
    }

    // -------- Teacher path: filtered visibility --------

    @Test
    fun `get groups as teacher returns managed groups`() = runRouteTest {
        val response = client.getWithAuth("/groups", teacherToken())
            .assertOK()
            .parse<AdminService.ListGroupsResponse>()

        assertEquals(TestGroupService.GROUP_IDS.size, response.groups.size)
        val ids = response.groups.map { it.id }.toSet()
        assertTrue(TestGroupService.GROUP_IDS.all { it in ids })
    }

    @Test
    fun `get group by id as teacher in managed set succeeds`() = runRouteTest {
        val group = client.getWithAuth("/groups/$ENROLLMENT_GROUP_ID", teacherToken())
            .assertOK()
            .parse<Group>()

        assertEquals(ENROLLMENT_GROUP_ID, group.id)
    }

    @Test
    fun `get group by id as teacher not in managed set returns 404`() = runRouteTest {
        client.getWithAuth("/groups/$UNUSED_ID", teacherToken())
            .assertNotFound("Group not found")
    }

    @Test
    fun `get group members as teacher not in managed set returns 404`() = runRouteTest {
        client.getWithAuth("/groups/$UNUSED_ID/members", teacherToken())
            .assertNotFound("Group not found")
    }

    @Test
    fun `get group managers as teacher in managed set succeeds`() = runRouteTest {
        val response = client.getWithAuth("/groups/$SUBJECT_GROUP_ID/managers", teacherToken())
            .assertOK()
            .parse<AdminService.ListUsersResponse>()

        assertEquals(0, response.total)
    }

    @Test
    fun `get group managers as teacher not in managed set returns 404`() = runRouteTest {
        client.getWithAuth("/groups/$UNUSED_ID/managers", teacherToken())
            .assertNotFound("Group not found")
    }

    @Test
    fun `get group member counts as teacher returns only managed counts`() = runRouteTest {
        val response = client.getWithAuth("/groups/member-counts", teacherToken())
            .assertOK()
            .parse<AdminService.GroupMemberCounts>()

        assertEquals(TestGroupService.GROUP_IDS.toSet(), response.member_counts.keys)
    }

    @Test
    fun `get groups as student is forbidden`() = runRouteTest {
        // authenticated(ELEVATED_USER_ONLY) returns 401 for non-elevated users
        client.getWithAuth("/groups", studentToken()).assertUnauthorized()
    }

    // -------- Mutating routes: admin-only --------

    @Test
    fun `patch group as teacher returns unauthorized`() = runRouteTest {
        client.patchProtoWithAuth(
            "/groups/$ENROLLMENT_GROUP_ID",
            AdminService.GroupPatch(name = "Nope"),
            teacherToken(),
        ).assertUnauthorized()
    }

    @Test
    fun `put group as teacher returns unauthorized`() = runRouteTest {
        client.putProtoWithAuth(
            "/groups/$ENROLLMENT_GROUP_ID",
            Group(id = ENROLLMENT_GROUP_ID, name = "Nope"),
            teacherToken(),
        ).assertUnauthorized()
    }

    @Test
    fun `delete group as teacher returns unauthorized`() = runRouteTest {
        client.deleteWithAuth("/groups/$ENROLLMENT_GROUP_ID", teacherToken())
            .assertUnauthorized()
    }

    @Test
    fun `delete group members as teacher returns unauthorized`() = runRouteTest {
        client.deleteWithAuth("/groups/$ENROLLMENT_GROUP_ID/members", teacherToken())
            .assertUnauthorized()
    }

    @Test
    fun `migrate group members as teacher returns unauthorized`() = runRouteTest {
        client.postWithAuth(
            "/groups/$ENROLLMENT_GROUP_ID/members/migrate?target_group_id=$SUBJECT_GROUP_ID",
            teacherToken(),
        ).assertUnauthorized()
    }

    @Test
    fun `delete group members as student returns unauthorized`() = runRouteTest {
        client.deleteWithAuth("/groups/$ENROLLMENT_GROUP_ID/members", studentToken())
            .assertUnauthorized()
    }
}

private suspend fun HttpClient.postWithAuth(url: String, token: String): HttpResponse =
    post(url) { bearerAuth(token) }
