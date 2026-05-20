package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IdTable

object Groups : IdTable<Int>("groups") {
    override val id = integer("id").entityId()

    val name = varchar("name", 255)

    /**
     * See [th.ac.bodin2.electives.proto.api.GroupType]
     */
    val type = integer("type")
    val parentId = reference(
        "parent_id",
        Groups,
        onDelete = ReferenceOption.SET_NULL,
        onUpdate = ReferenceOption.CASCADE
    ).nullable().index()

    override val primaryKey = PrimaryKey(id)
}
