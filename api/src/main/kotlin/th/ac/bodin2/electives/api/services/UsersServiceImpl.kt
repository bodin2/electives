package th.ac.bodin2.electives.api.services

import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.Team
import th.ac.bodin2.electives.db.User
import th.ac.bodin2.electives.db.exists
import th.ac.bodin2.electives.db.models.StudentTeams
import th.ac.bodin2.electives.db.models.Students
import th.ac.bodin2.electives.db.models.Teachers
import th.ac.bodin2.electives.db.models.Users
import th.ac.bodin2.electives.proto.api.AdminService
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.utils.Argon2
import th.ac.bodin2.electives.utils.withMinimumDelay
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class UsersServiceImpl(val config: Config) : UsersService {
    companion object {
        private const val TOKEN_SIZE = 32
        private val logger = LoggerFactory.getLogger(UsersServiceImpl::class.java)
    }

    class Config(val sessionDurationSeconds: Long, val minimumSessionCreationTime: Duration)

    // 4 MB cache for user types
    private val userTypeCache = InMemoryKache<Int, UserType>(maxSize = 4 * 1024 * 1024) {
        strategy = KacheStrategy.LRU
        expireAfterWriteDuration = 10.minutes
    }

    /**
     * Gets the [UserType] of the given user ID.
     *
     * **Do not assume the user's existence after calling this method successfully.**
     * Values are LRU-cached with TTL after each database read, see [userTypeCache].
     *
     * @throws NotFoundException if the user does not exist.
     * @throws IllegalStateException if the user is neither a Student nor a Teacher.
     */
    override fun getUserType(id: Int): UserType {
        runBlocking { userTypeCache.get(id) }?.let { return it }

        val query = Users
            .leftJoin(Students)
            .leftJoin(Teachers)
            .select(Users.id, Students.id, Teachers.id)
            .where { Users.id eq id }
            .map {
                when {
                    it.getOrNull(Students.id) != null -> UserType.STUDENT
                    it.getOrNull(Teachers.id) != null -> UserType.TEACHER
                    else -> throw IllegalStateException("User is not a Student or a Teacher: $id")
                }
            }.firstOrNull()

        query ?: throw NotFoundException(NotFoundEntity.USER, "User does not exist: $id")

        runBlocking { userTypeCache.put(id, query) }
        return query
    }

    override fun createStudent(
        id: Int,
        firstName: String,
        middleName: String?,
        lastName: String?,
        password: String,
        avatarUrl: String?,
    ): Student = Student.new(id, createUser(id, firstName, middleName, lastName, password, avatarUrl))

    override fun createTeacher(
        id: Int,
        firstName: String,
        middleName: String?,
        lastName: String?,
        password: String,
        avatarUrl: String?,
    ): Teacher = Teacher.new(id, createUser(id, firstName, middleName, lastName, password, avatarUrl))

    @CreatesTransaction
    override fun deleteUser(id: Int) {
        val rows = transaction { Users.deleteWhere { Users.id eq id } }
        if (rows == 0) {
            throw NotFoundException(NotFoundEntity.USER, "User does not exist: $id")
        }

        runBlocking {
            val lastKnownType = userTypeCache.remove(id)
            logger.info("Deleted user (type: ${lastKnownType ?: "<not cached>"}): $id")
        }
    }

    @CreatesTransaction
    override fun updateStudent(id: Int, update: UsersService.StudentUpdate) {
        transaction {
            Student.require(id)

            updateUser(id, update.update)

            if (update.teams != null) {
                StudentTeams.deleteWhere { StudentTeams.student eq id }
                StudentTeams.batchInsert(update.teams) { teamId ->
                    if (!Team.exists(id)) throw NotFoundException(NotFoundEntity.TEAM)

                    this[StudentTeams.student] = id
                    this[StudentTeams.team] = teamId
                }
            }
        }
    }

    @CreatesTransaction
    override fun updateTeacher(id: Int, update: UsersService.TeacherUpdate) {
        transaction {
            Teacher.require(id)

            updateUser(id, update.update)
        }
    }

    private fun updateUser(id: Int, update: UsersService.UserUpdate) {
        Users.update(where = { Users.id eq id }) {
            with(update) {
                if (firstName != null) it[Users.firstName] = firstName
                if (setMiddleName) it[Users.middleName] = middleName
                if (lastName != null) it[Users.lastName] = lastName
                if (setAvatarUrl) it[Users.avatarUrl] = avatarUrl
            }
        }
    }

    @CreatesTransaction
    override fun setPassword(id: Int, newPassword: String) {
        val password = newPassword.trim()
        require(password.length > 4) { "Password must be more than 4 characters once trimmed" }
        require(password.length <= 4096) { "Password must have less or equal to 4096 characters" }

        val rows = transaction {
            Users.update(where = { Users.id eq id }) {
                it[Users.passwordHash] = Argon2.hash(password.toCharArray())
            }
        }

        if (rows == 0) throw NotFoundException(NotFoundEntity.USER, "User does not exist: $id")
    }

    override fun getTeacherById(id: Int): Teacher? = Teacher.findById(id)

    override fun getStudentById(id: Int): Student? = Student.findById(id)

    @CreatesTransaction
    override suspend fun createSession(id: Int, password: String, aud: String): String =
        withMinimumDelay(config.minimumSessionCreationTime) {
            require(password.length <= 4096) { "Password too long for user: $id" }

            val aud = aud.trim().apply {
                require(!isEmpty()) { "Audience blank for user: $id" }
                require(length <= 256) { "Audience string too long for user: $id (aud = ${slice(0..32)}...)" }
            }

            transaction {
                val user = Users.select(Users.passwordHash).where { Users.id eq id }.singleOrNull()
                    ?: throw NotFoundException(NotFoundEntity.USER, "User does not exist: $id")

                val passwordHash = user[Users.passwordHash]

                require(
                    Argon2.verify(passwordHash.toByteArray(), password.toCharArray())
                ) { "Invalid password for user: $id" }

                val session = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(ByteArray(TOKEN_SIZE).apply {
                        SecureRandom().nextBytes(this)
                    })

                Users.update({ Users.id eq id }) {
                    it[Users.sessionExpiry] = LocalDateTime.now().plusSeconds(config.sessionDurationSeconds)
                    it[Users.sessionHash] = Argon2.hash(session.toCharArray())
                }

                logger.debug("New session created, user: $id, aud: $aud")

                "$id.$session"
            }
        }

    override fun getSessionUserId(token: String): Int {
        val (subject, session) = token.split(".", limit = 2).takeIf { it.size == 2 }
            ?: throw IllegalArgumentException("Invalid session token format")

        val userId = subject.toIntOrNull() ?: throw IllegalArgumentException("Invalid token subject: $subject")

        val user = Users.select(Users.sessionHash, Users.sessionExpiry).where { Users.id eq userId }.singleOrNull()
            ?: throw NotFoundException(NotFoundEntity.USER, "User does not exist: $userId")

        val sessionExpiry = user[Users.sessionExpiry]
            ?: throw IllegalArgumentException("No active session for user: $userId")

        val sessionHash = user[Users.sessionHash]
            ?: throw IllegalArgumentException("No active session for user: $userId")

        if (sessionExpiry.isBefore(LocalDateTime.now()))
            throw IllegalArgumentException("Session expired for user: $userId")

        if (!Argon2.verify(sessionHash.toByteArray(), session.toCharArray()))
            throw IllegalArgumentException("Invalid session token for user: $userId")

        logger.debug("Validated session token, user: $userId")

        return userId
    }


    override fun clearSession(userId: Int) {
        Users.update({ Users.id eq userId }) {
            it[Users.sessionExpiry] = null
            it[Users.sessionHash] = null
        }

        logger.debug("Session cleared, user: $userId")
    }

    private fun createUser(
        id: Int,
        firstName: String,
        middleName: String?,
        lastName: String?,
        password: String,
        avatarUrl: String?,
    ) = User.wrapRow(Users.insert {
        it[Users.id] = id
        it[Users.firstName] = firstName
        it[Users.middleName] = middleName
        it[Users.lastName] = lastName
        it[Users.passwordHash] = Argon2.hash(password.toCharArray())
        it[Users.avatarUrl] = avatarUrl
    }.resultedValues!!.first())
}