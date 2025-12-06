package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Electives are a collection of subjects that students can choose from.
 * Each elective can be associated with a team, allowing only students from that team to access the elective to enroll in its subjects.
 */
object Electives : IdTable<Int>("electives") {
    override val id = integer("id").entityId()

    val name = varchar("name", 255)

    /**
     * The team that this elective belongs to. An elective does not need to be associated with a team.
     *
     * If set, only users who belong to this team can access this elective.
     */
    val team = reference("team_id", Teams).nullable()

    val startDate = datetime("start_date").nullable()
    val endDate = datetime("end_date").nullable()

    override val primaryKey = PrimaryKey(id)
}