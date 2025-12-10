package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Students : IdTable<Int>("students") {
    override val id = reference("id", Users, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(id)
}