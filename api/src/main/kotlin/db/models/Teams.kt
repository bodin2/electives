package th.ac.bodin2.electives.api.db.models

import org.jetbrains.exposed.dao.id.IdTable

object Teams : IdTable<Int>("teams") {
    override val id = integer("id").entityId()

    val name = varchar("name", 255)

    override val primaryKey = PrimaryKey(id)
}