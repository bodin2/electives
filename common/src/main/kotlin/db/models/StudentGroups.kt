package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object StudentGroups : Table("student_groups") {
    val student =
        reference("student_id", Students, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val group =
        reference("group_id", Groups, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE).index()
    override val primaryKey = PrimaryKey(student, group)
}
