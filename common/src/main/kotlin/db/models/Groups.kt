package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.dao.id.IdTable

object Groups : IdTable<Int>("groups") {
    override val id = integer("id").entityId()

    val name = varchar("name", 255)

    override val primaryKey = PrimaryKey(id)
}
