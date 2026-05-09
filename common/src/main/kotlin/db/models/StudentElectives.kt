package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object StudentElectives : Table("students_to_electives") {
    val student = reference("student_id", Students, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val elective = reference("elective_id", Electives, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val subject = reference("subject_id", Subjects, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(student, elective)

    init {
        // For:
        //   - WHERE elective = ?  (enrolled count, members)
        //   - WHERE subject = ? AND elective = ?  (students enrolled in an enrollment)
        //   - WHERE elective = ? AND subject IN (...), Count(student)  (per-subject enrolled counts)
        index(false, elective, subject, student)
    }
}