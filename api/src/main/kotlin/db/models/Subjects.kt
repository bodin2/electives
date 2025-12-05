package th.ac.bodin2.electives.api.db.models

import org.jetbrains.exposed.dao.id.IdTable

object Subjects : IdTable<Int>("subjects") {
    override val id = integer("id").entityId()

    /**
     * The team that this subject belongs to. A subject does not need to be associated with a team.
     *
     * If a team is specified, only students in that team can enroll in this subject.
     * Useful for special subjects that are only available to certain teams.
     *
     * ### Example use case
     *
     * - You have an elective for the team `A`. This subject belongs to this elective.
     * - You want to restrict the subject, only to those who belong to the `A` and `B` team.
     *
     * In this case:
     *
     * - You set the subject's elective's team to `A`.
     * - You set the subject's team to `B`.
     */
    val team = reference("team_id", Teams).nullable()

    val name = varchar("name", 255)
    val description = varchar("description", 10000).nullable()
    val code = varchar("code", 127).nullable()

    /**
     * [th.ac.bodin2.electives.proto.api.SubjectTag] for the subject.
     */
    val tag = integer("tag")

    val location = varchar("location", 255).nullable()
    val capacity = integer("max_students")

    override val primaryKey = PrimaryKey(id)
}