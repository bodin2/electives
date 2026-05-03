package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Team
import th.ac.bodin2.electives.db.models.StudentTeams
import th.ac.bodin2.electives.db.models.Students
import th.ac.bodin2.electives.db.models.Teams

class TeamServiceImpl : TeamService {
    companion object {
        private const val PAGE_SIZE = 50
    }

    @Transactional
    override fun create(
        id: Int,
        name: String,
    ) = transaction {
        val stmt = Teams.insertIgnore {
            it[this.id] = id
            it[this.name] = name
        }

        if (stmt.insertedCount == 0) throw ConflictException(ExceptionEntity.TEAM)
        Team.wrapRow(stmt.resultedValues!!.first())
    }

    @Transactional
    override fun delete(id: Int) {
        transaction {
            val rows = Teams.deleteWhere { Teams.id eq id }
            if (rows == 0) {
                throw EntityNotFoundException(ExceptionEntity.TEAM)
            }
        }
    }

    @Transactional
    override fun update(id: Int, update: TeamService.TeamUpdate) = transaction {
        Team.findById(id) ?: throw EntityNotFoundException(ExceptionEntity.TEAM)
        val rows = Teams.updateReturning(where = { Teams.id eq id }) {
            update.name?.let { name -> it[this.name] = name }

            if (it.firstDataSet.isEmpty()) throw NothingToUpdateException()
        }
        Team.wrapRow(rows.first())
    }

    override fun getAll() = Team.all().toList()

    override fun getById(teamId: Int) = Team.findById(teamId)

    @Transactional
    override fun getMembers(teamId: Int, page: Int): Pair<List<Student>, Long> = transaction {
        Team.findById(teamId) ?: throw EntityNotFoundException(ExceptionEntity.TEAM)

        val query = StudentTeams.selectAll().where { StudentTeams.team eq teamId }
        val count = query.count()

        val offset = ((page - 1) * PAGE_SIZE).toLong()
        val members = (StudentTeams innerJoin Students)
            .selectAll().where { StudentTeams.team eq teamId }
            .limit(PAGE_SIZE)
            .offset(offset)
            .let { Student.wrapRows(it) }
            .with(Student::user, Student::teams)
            .toList()

        members to count
    }

    override fun getMemberCounts() = StudentTeams.select(StudentTeams.team, StudentTeams.student.count())
        .groupBy(StudentTeams.team)
        .associate { it[StudentTeams.team].value to it[StudentTeams.student.count()].toInt() }
}
