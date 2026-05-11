package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.db.Group
import th.ac.bodin2.electives.db.Student
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

    fun getMemberCounts(): Map<Int, Int>

    fun getMemberCount(groupId: Int): Int
}
