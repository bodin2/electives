package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/**
 * Join table associating teachers with their groups.
 */
object TeacherGroups : Table("teacher_groups") {
    val teacher =
        reference("teacher_id", Teachers, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val group =
        reference("group_id", Groups, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE).index()
    override val primaryKey = PrimaryKey(teacher, group)
}
