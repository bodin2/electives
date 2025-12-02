package th.ac.bodin2.electives.api.db.models

import org.jetbrains.exposed.sql.Table

object TeacherSubjects : Table("teachers_to_subjects") {
    val teacher = reference("teacher_id", Teachers.id)
    val subject = reference("subject_id", Subjects.id)

    override val primaryKey = PrimaryKey(teacher, subject)
}