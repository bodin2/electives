package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object EnrollmentSubjects : Table("enrollment_subjects") {
    val enrollment =
        reference("enrollment_id", Enrollments, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val subject =
        reference("subject_id", Subjects, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(enrollment, subject)

    init {
        // PK already covers (enrollment, subject). This is for looking up subjects of an enrollment
        index(false, subject, enrollment)
    }
}
