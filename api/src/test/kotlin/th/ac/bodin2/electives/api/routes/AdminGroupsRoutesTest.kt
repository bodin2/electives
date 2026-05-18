package th.ac.bodin2.electives.api.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.getWithAuth
import th.ac.bodin2.electives.api.parse
import th.ac.bodin2.electives.api.patchProtoWithAuth
import th.ac.bodin2.electives.api.services.mock.TestGroupService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ADMIN_TOKEN
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_GROUP_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.proto.api.AdminService
import th.ac.bodin2.electives.proto.api.Group
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminGroupsRoutesTest : ApplicationTest() {
    private suspend fun HttpClient.adminGet(url: String): HttpResponse =
        getWithAuth(url, ADMIN_TOKEN)

    @Test
    fun `get groups without auth returns unauthorized`() = runRouteTest {
        client.get("/admin/groups").assertUnauthorized()
    }

    @Test
    fun `get groups list`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/groups")
            .assertOK()
            .parse<AdminService.ListGroupsResponse>()

        assertEquals(TestGroupService.GROUP_IDS.size, response.groups.size)
    }

    @Test
    fun `get group by id`() = runRouteTest {
        startApplication()

        val group = client.adminGet("/admin/groups/$ENROLLMENT_GROUP_ID")
            .assertOK()
            .parse<Group>()

        assertEquals(ENROLLMENT_GROUP_ID, group.id)
    }

    @Test
    fun `get group not found`() = runRouteTest {
        startApplication()

        client.adminGet("/admin/groups/$UNUSED_ID").assertNotFound()
    }

    @Test
    fun `delete group without auth returns unauthorized`() = runRouteTest {
        client.delete("/admin/groups/$ENROLLMENT_GROUP_ID").assertUnauthorized()
    }

    @Test
    fun `get group member counts without auth returns unauthorized`() = runRouteTest {
        client.get("/admin/groups/member-counts").assertUnauthorized()
    }

    @Test
    fun `get group member counts`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/groups/member-counts")
            .assertOK()
            .parse<AdminService.GroupMemberCounts>()

        assertEquals(TestGroupService.GROUP_IDS.size, response.member_counts.size)
        TestGroupService.GROUP_IDS.forEach { groupId ->
            assertEquals(0, response.member_counts[groupId])
        }
    }

    @Test
    fun `get group members without auth returns unauthorized`() = runRouteTest {
        client.get("/admin/groups/$ENROLLMENT_GROUP_ID/members").assertUnauthorized()
    }

    @Test
    fun `get group members`() = runRouteTest {
        startApplication()

        val response = client.adminGet("/admin/groups/$ENROLLMENT_GROUP_ID/members")
            .assertOK()
            .parse<AdminService.ListUsersResponse>()

        assertEquals(0, response.total)
        assertEquals(0, response.users.size)
    }

    @Test
    fun `get group members not found`() = runRouteTest {
        startApplication()

        client.adminGet("/admin/groups/$UNUSED_ID/members").assertNotFound()
    }

    @Test
    fun `patch group without auth returns unauthorized`() = runRouteTest {
        client.patch("/admin/groups/$ENROLLMENT_GROUP_ID").assertUnauthorized()
    }

    @Test
    fun `patch group returns updated group`() = runRouteTest {
        startApplication()

        val group = client.patchProtoWithAuth(
            "/admin/groups/$ENROLLMENT_GROUP_ID",
            AdminService.GroupPatch(name = "New Name"),
            ADMIN_TOKEN
        ).assertOK().parse<Group>()

        assertEquals(ENROLLMENT_GROUP_ID, group.id)
    }

    @Test
    fun `patch group not found`() = runRouteTest {
        startApplication()

        client.patchProtoWithAuth(
            "/admin/groups/$UNUSED_ID",
            AdminService.GroupPatch(name = "New Name"),
            ADMIN_TOKEN
        ).assertNotFound("Group not found")
    }
}
