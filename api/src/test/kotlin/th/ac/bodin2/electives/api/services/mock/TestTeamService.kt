package th.ac.bodin2.electives.api.services.mock

import th.ac.bodin2.electives.api.MockUtils
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.api.services.TeamService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ELECTIVE_TEAM_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_TEAM_ID
import th.ac.bodin2.electives.db.Team

class TestTeamService : TeamService {
    companion object {
        val TEAM_IDS = listOf(ELECTIVE_TEAM_ID, SUBJECT_TEAM_ID)
    }

    @CreatesTransaction
    override fun create(id: Int, name: String): Team = error("Not testable")

    @CreatesTransaction
    override fun delete(id: Int) = error("Not testable")

    @CreatesTransaction
    override fun update(id: Int, update: TeamService.TeamUpdate) = error("Not testable")

    override fun getAll() = TEAM_IDS.map { id -> MockUtils.mockTeam(id) }

    override fun getById(teamId: Int) =
        if (teamId in TEAM_IDS) MockUtils.mockTeam(teamId)
        else null
}
