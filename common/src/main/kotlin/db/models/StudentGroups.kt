package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/**
 * Join table associating students with their groups.
 *
 * A student row must, by service-layer invariant, have exactly one membership
 * whose group has `type = GRADE`, one with `type = ROOM`, **optionally** one with `type = PROGRAM`,
 * plus zero or more memberships with `type = CUSTOM`.
 */
object StudentGroups : Table("student_groups") {
    val student =
        reference("student_id", Students, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val group =
        reference("group_id", Groups, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE).index()
    override val primaryKey = PrimaryKey(student, group)
}
