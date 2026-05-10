package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IdTable

object Students : IdTable<Int>("students") {
    override val id = integer("id").references(Users.id, onDelete = ReferenceOption.CASCADE).entityId()
    override val primaryKey = PrimaryKey(id)
}