package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.Table

object ElectiveSubjects : Table("electives_to_subjects") {
    val elective = reference("elective_id", Electives.id)
    val subject = reference("subject_id", Subjects.id)
    override val primaryKey = PrimaryKey(elective, subject)
}