package th.ac.bodin2.electives.api.db.models

import org.jetbrains.exposed.sql.Table

object SubjectTeams : Table("subjects_to_teams") {
    val subject = reference("subject_id", Subjects.id)
    val team = reference("team_id", Teams.id)

    override val primaryKey = PrimaryKey(subject, team)
}