package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.db.Team
import th.ac.bodin2.electives.db.models.Teams

class TeamServiceImpl : TeamService {
    @CreatesTransaction
    override fun create(
        id: Int,
        name: String,
    ) = transaction {
        Team.wrapRow(Teams.insert {
            it[this.id] = id
            it[this.name] = name
        }.resultedValues!!.first())
    }

    @CreatesTransaction
    override fun delete(id: Int) {
        transaction {
            val rows = Teams.deleteWhere { Teams.id eq id }
            if (rows == 0) {
                throw NotFoundException(NotFoundEntity.TEAM)
            }
        }
    }

    @CreatesTransaction
    override fun update(id: Int, update: TeamService.TeamUpdate) {
        transaction {
            Team.findById(id) ?: throw NotFoundException(NotFoundEntity.TEAM)
            Teams.update({ Teams.id eq id }) {
                update.name?.let { name -> it[this.name] = name }

                if (it.firstDataSet.isEmpty()) throw NothingToUpdateException()
            }
        }
    }

    override fun getAll() = Team.all().toList()

    override fun getById(teamId: Int) = Team.findById(teamId)
}
