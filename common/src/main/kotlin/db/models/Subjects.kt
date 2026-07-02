package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IdTable

object Subjects : IdTable<Int>("subjects") {
    override val id = integer("id").entityId()

    /**
     * The group that this subject belongs to. A subject does not need to be associated with a group.
     *
     * If a group is specified, only students in that group can enroll in this subject.
     * Useful for special subjects that are only available to certain groups.
     *
     * Subject group restrictions are applied in addition to enrollment group restrictions.
     * A student must satisfy both the enrollment's group and the subject's group restrictions to enroll in the subject.
     *
     * A student can enroll multiple times in the same subject if the subject belongs to multiple enrollments.
     *
     * ### Limitations
     *
     * A subject cannot have multiple groups.
     * To achieve similar functionality, assign the same group to the students who can pick this subject.
     *
     * A subject cannot have different details per enrollment.
     * To achieve similar functionality, simply create another subject with similar details.
     *
     * A subject cannot have different teachers per enrollment.
     * To achieve similar functionality, simply create another subject with same details and assign different teachers.
     *
     * ### Example use case
     *
     * - You have an enrollment for the group `A`. This subject belongs to this enrollment.
     * - You want to restrict the subject, only to those who belong to the `A` and `B` group.
     *
     * In this case:
     *
     * - You set the subject's enrollment's group to `A`.
     * - You set the subject's group to `B`.
     */
    val group = reference(
        "group_id",
        Groups,
        onDelete = ReferenceOption.RESTRICT,
        onUpdate = ReferenceOption.CASCADE
    ).nullable().index()

    val name = varchar("name", 255)
    val description = varchar("description", 10000).nullable()
    val code = varchar("code", 127).nullable()

    /**
     * [th.ac.bodin2.electives.proto.api.SubjectTag] for the subject.
     */
    val tag = integer("tag")

    val location = varchar("location", 255).nullable()
    val capacity = integer("max_students")

    val imageUrl = varchar("image_url", 1000).nullable()
    val thumbnailUrl = varchar("thumbnail_url", 1000).nullable()

    override val primaryKey = PrimaryKey(id)
}