package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Students : Table("students") {
    val id = reference("id", Users, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(id)
}