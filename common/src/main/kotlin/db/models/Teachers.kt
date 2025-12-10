package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Teachers : IdTable<Int>("teachers") {
    override val id = reference("id", Users, onDelete = ReferenceOption.CASCADE)
    val avatar = blob("avatar").nullable()
    override val primaryKey = PrimaryKey(id)
}