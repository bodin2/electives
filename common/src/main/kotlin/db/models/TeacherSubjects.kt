package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object TeacherSubjects : Table("teachers_to_subjects") {
    val teacher =
        reference("teacher_id", Teachers, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val subject =
        reference("subject_id", Subjects, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val elective =
        reference("elective_id", Electives, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(teacher, subject, elective)

    init {
        // For: WHERE subject = ? AND elective = ?
        index(false, subject, elective)
    }
}