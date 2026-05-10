package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object StudentClasses : Table("student_classes") {
    val student =
        reference("student_id", Students, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val enrollment =
        reference("enrollment_id", Enrollments, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val subject =
        reference("subject_id", Subjects, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(student, enrollment)

    init {
        // For:
        //   - WHERE enrollment = ?  (enrolled count, members)
        //   - WHERE subject = ? AND enrollment = ?  (students enrolled in an enrollment)
        //   - WHERE enrollment = ? AND subject IN (...), Count(student)  (per-subject enrolled counts)
        index(false, enrollment, subject, student)
    }
}
