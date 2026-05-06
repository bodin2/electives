package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UIntIdTable

object Admins : UIntIdTable("admins") {
    val user = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val publicKey = varchar("public_key", 4096)
}
