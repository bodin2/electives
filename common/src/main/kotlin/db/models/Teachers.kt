package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UIntIdTable

object Teachers : UIntIdTable("teachers") {
    val user = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
}