package th.ac.bodin2.electives.api.db.models

import org.jetbrains.exposed.sql.Table

object StudentElectives : Table("students_to_electives") {
    val student = reference("student_id", Students.id)
    val elective = reference("elective_id", Electives.id)

    val subject = reference("subject_id", Subjects.id)


    override val primaryKey = PrimaryKey(student, elective)
}