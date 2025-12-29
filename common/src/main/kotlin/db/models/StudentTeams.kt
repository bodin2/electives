package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.Table

object StudentTeams : Table("students_to_teams") {
    val student = reference("student_id", Students.id)
    val team = reference("team_id", Teams.id)

    override val primaryKey = PrimaryKey(student, team)
}