package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.db.Group
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.proto.api.GroupType

interface GroupService {
    /**
     * Creates a new group with the given information.
     *
     * @throws ConflictException if a group with the same ID already exists.
     */
    @Transactional
    fun create(
        id: Int,
        name: String,
        type: GroupType = GroupType.CUSTOM,
        parentId: Int? = null,
    ): Group

    /**
     * Deletes a group by its ID.
     *
     * @throws EntityNotFoundException if the group does not exist.
     */
    @Transactional
    fun delete(id: Int)

    /**
     * Updates a group's information.
     *
     * @throws EntityNotFoundException if the group does not exist.
     * @throws NothingToUpdateException if there's nothing to update.
     */
    @Transactional
    fun update(id: Int, update: GroupUpdate): Group

    data class GroupUpdate(
        val name: String? = null,
        val parentId: Int? = null,
        val setParentId: Boolean = false,
    )

    fun getAll(): List<Group>

    fun getById(groupId: Int): Group?

    /**
     * Gets a paginated list of group members, optionally filtered by a search query.
     *
     * When [query] is provided, results are filtered by substring match on ID, firstName, middleName, or lastName.
     *
     * @return Pair of member list and total count.
     * @throws EntityNotFoundException if the group does not exist.
     */
    @Transactional
    fun getMembers(groupId: Int, page: Int = 1, query: String? = null): Pair<List<Student>, Long>

    /**
     * Gets a paginated list of group managers (teachers), optionally filtered by a search query.
     *
     * When [query] is provided, results are filtered by substring match on ID, firstName, middleName, or lastName.
     *
     * @return Pair of teacher list and total count.
     * @throws EntityNotFoundException if the group does not exist.
     */
    @Transactional
    fun getManagers(groupId: Int, page: Int = 1, query: String? = null): Pair<List<Teacher>, Long>

    fun getMemberCounts(): Map<Int, Int>

    fun getMemberCount(groupId: Int): Int

    /**
     * Deletes all members of the specified group by removing the underlying
     * [th.ac.bodin2.electives.db.User] rows. Cascading deletes also remove the
     * associated [th.ac.bodin2.electives.db.Student] rows and their group
     * memberships. The group itself is left in place.
     *
     * @throws EntityNotFoundException if the group does not exist.
     */
    @Transactional
    fun deleteMembers(groupId: Int)

    /**
     * Moves all members of [groupId] into [targetGroupId]. The target group must
     * exist, be different from the source, and have the same [GroupType] as the
     * source. Memberships already present in the target are preserved.
     *
     * @throws EntityNotFoundException if either group does not exist.
     * @throws ConflictException if the groups are the same or have different types.
     */
    @Transactional
    fun migrateMembers(groupId: Int, targetGroupId: Int)
}
