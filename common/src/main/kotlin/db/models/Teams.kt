package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.dao.id.UIntIdTable

object Teams : UIntIdTable("teams") {
    val name = varchar("name", 255)
}