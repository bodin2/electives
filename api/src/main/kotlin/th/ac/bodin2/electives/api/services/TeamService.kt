package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Team

interface TeamService {
    /**
     * Creates a new team with the given information.
     *
     * @throws ConflictException if a team with the same ID already exists.
     */
    @Transactional
    fun create(
        id: Int,
        name: String,
    ): Team

    /**
     * Deletes a team by its ID.
     *
     * @throws EntityNotFoundException if the team does not exist.
     */
    @Transactional
    fun delete(id: Int)

    /**
     * Updates a team's information.
     *
     * @throws EntityNotFoundException if the team does not exist.
     * @throws NothingToUpdateException if there's nothing to update.
     */
    @Transactional
    fun update(id: Int, update: TeamUpdate): Team

    data class TeamUpdate(
        val name: String? = null,
    )

    fun getAll(): List<Team>

    fun getById(teamId: Int): Team?

    /**
     * Gets a paginated list of team members, optionally filtered by a search query.
     *
     * When [query] is provided, results are filtered by substring match on ID, firstName, middleName, or lastName.
     *
     * @return Pair of member list and total count.
     * @throws EntityNotFoundException if the team does not exist.
     */
    @Transactional
    fun getMembers(teamId: Int, page: Int = 1, query: String? = null): Pair<List<Student>, Long>

    fun getMemberCounts(): Map<Int, Int>

    fun getMemberCount(teamId: Int): Int
}
