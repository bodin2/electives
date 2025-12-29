package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IdTable

object Teachers : IdTable<Int>("teachers") {
    override val id = reference("id", Users, onDelete = ReferenceOption.CASCADE)
    val avatar = blob("avatar").nullable()
    override val primaryKey = PrimaryKey(id)
}