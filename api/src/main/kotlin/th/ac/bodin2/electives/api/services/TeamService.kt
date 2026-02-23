package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.db.Team

interface TeamService {
    /**
     * Creates a new team with the given information.
     *
     * @throws ConflictException if a team with the same ID already exists.
     */
    @CreatesTransaction
    fun create(
        id: Int,
        name: String,
    ): Team

    /**
     * Deletes a team by its ID.
     *
     * @throws NotFoundException if the team does not exist.
     */
    @CreatesTransaction
    fun delete(id: Int)

    /**
     * Updates a team's information.
     *
     * @throws NotFoundException if the team does not exist.
     * @throws NothingToUpdateException if there's nothing to update.
     */
    @CreatesTransaction
    fun update(id: Int, update: TeamUpdate)

    data class TeamUpdate(
        val name: String? = null,
    )

    fun getAll(): List<Team>

    fun getById(teamId: Int): Team?
}
