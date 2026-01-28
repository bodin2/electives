package th.ac.bodin2.electives.db.models

import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.javatime.datetime

object Users : IdTable<Int>("users") {
    override val id = integer("id").entityId()

    val firstName = varchar("first_name", 255)
    val middleName = varchar("middle_name", 255).nullable()
    val lastName = varchar("last_name", 255).nullable()

    val avatarUrl = varchar("avatar_url", 1000).nullable()

    val passwordHash = varchar("hash", 255)

    /**
     * Hash of the session token.
     */
    val sessionHash = varchar("session_hash", 255).nullable()

    /**
     * Expiry time of the current session.
     * If not set, consider the session expired.
     */
    val sessionExpiry = datetime("session_expiry").nullable()

    override val primaryKey = PrimaryKey(id)
}