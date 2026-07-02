package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IdTable

object Teachers : IdTable<Int>("teachers") {
    override val id = integer("id").references(Users.id, onDelete = ReferenceOption.CASCADE).entityId()
    override val primaryKey = PrimaryKey(id)
}