package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.db.Team

interface TeamService {
    @CreatesTransaction
    fun create(
        id: Int,
        name: String,
    ): Team

    /**
     * Deletes a team by its ID.
     *
     * @throws th.ac.bodin2.electives.NotFoundException if the team does not exist.
     */
    @CreatesTransaction
    fun delete(id: Int)

    /**
     * Updates a team's information.
     *
     * @throws th.ac.bodin2.electives.NotFoundException if the team does not exist.
     */
    @CreatesTransaction
    fun update(id: Int, update: TeamUpdate)

    data class TeamUpdate(
        val name: String? = null,
    )

    fun getAll(): List<Team>

    fun getById(teamId: Int): Team?
}
