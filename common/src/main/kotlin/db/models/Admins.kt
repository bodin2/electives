package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IdTable

object Admins : IdTable<Int>("admins") {
    override val id = integer("id").references(Users.id, onDelete = ReferenceOption.CASCADE).entityId()
    override val primaryKey = PrimaryKey(id)

    val publicKey = varchar("public_key", 4096)
}
