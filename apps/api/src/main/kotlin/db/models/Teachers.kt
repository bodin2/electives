package th.ac.bodin2.electives.api.db.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Teachers : Table("teachers") {
    val id = reference("id", Users, onDelete = ReferenceOption.CASCADE)
    val avatar = blob("avatar").nullable()

    override val primaryKey = PrimaryKey(id)
}