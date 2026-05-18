package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.javatime.datetime

/**
 * Enrollments are a collection of subjects that students can choose from.
 * Each enrollment can be associated with a group, allowing only students from that group to access the enrollment to enroll in its subjects.
 */
object Enrollments : IdTable<Int>("enrollments") {
    override val id = integer("id").entityId()

    val name = varchar("name", 255)

    /**
     * The group that this enrollment belongs to. An enrollment does not need to be associated with a group.
     *
     * If set, only users who belong to this group can access this enrollment.
     */
    val group = reference(
        "group_id",
        Groups,
        onDelete = ReferenceOption.RESTRICT,
        onUpdate = ReferenceOption.CASCADE
    ).nullable().index()

    val startDate = datetime("start_date").nullable()
    val endDate = datetime("end_date").nullable()

    override val primaryKey = PrimaryKey(id)
}
