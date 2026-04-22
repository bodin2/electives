package th.ac.bodin2.electives.api.services.mock

import th.ac.bodin2.electives.api.MockUtils
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.TeamService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ELECTIVE_TEAM_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_TEAM_ID
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Team

class TestTeamService : TeamService {
    companion object {
        val TEAM_IDS = listOf(ELECTIVE_TEAM_ID, SUBJECT_TEAM_ID)
    }

    @Transactional
    override fun create(id: Int, name: String): Team = error("Not testable")

    @Transactional
    override fun delete(id: Int) = error("Not testable")

    @Transactional
    override fun update(id: Int, update: TeamService.TeamUpdate) = error("Not testable")

    override fun getAll() = TEAM_IDS.map { id -> MockUtils.mockTeam(id) }

    override fun getById(teamId: Int) =
        if (teamId in TEAM_IDS) MockUtils.mockTeam(teamId)
        else null

    @Transactional
    override fun getMembers(teamId: Int, page: Int): Pair<List<Student>, Long> {
        if (teamId !in TEAM_IDS) throw EntityNotFoundException(ExceptionEntity.TEAM)
        return emptyList<Student>() to 0L
    }

    override fun getMemberCounts() = TEAM_IDS.associateWith { 0 }
}
