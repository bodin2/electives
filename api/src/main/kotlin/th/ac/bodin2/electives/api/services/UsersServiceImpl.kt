package th.ac.bodin2.electives.api.services

import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.User
import th.ac.bodin2.electives.db.models.Students
import th.ac.bodin2.electives.db.models.Teachers
import th.ac.bodin2.electives.db.models.Users
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.utils.Argon2
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration.Companion.minutes

class UsersServiceImpl(val config: Config) : UsersService {
    companion object {
        private const val TOKEN_SIZE = 32
        private val logger = LoggerFactory.getLogger(UsersServiceImpl::class.java)
    }

    interface Config {
        val sessionDurationSeconds: Long
    }

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
    ): Student = Student.new(id, createUser(id, firstName, middleName, lastName, password))

    override fun createTeacher(
        id: Int,
        firstName: String,
        middleName: String?,
        lastName: String?,
        password: String,
        avatar: ByteArray?,
    ): Teacher = Teacher.new(id, createUser(id, firstName, middleName, lastName, password), avatar)

    override fun getTeacherById(id: Int): Teacher? = Teacher.findById(id)

    override fun getStudentById(id: Int): Student? = Student.findById(id)

    override fun createSession(id: Int, password: String, aud: String): String {
        if (password.length > 4096) {
            throw IllegalArgumentException("Password too long for user: $id")
        }

        val user = Users.select(Users.passwordHash).where { Users.id eq id }.singleOrNull()
            ?: throw NotFoundException(NotFoundEntity.USER, "User does not exist: $id")

        val passwordHash = user[Users.passwordHash]

        return if (Argon2.verify(passwordHash.toByteArray(), password.toCharArray())) {
            val session = Base64.getEncoder()
                .withoutPadding()
                .encodeToString(ByteArray(TOKEN_SIZE).apply {
                    SecureRandom().nextBytes(this)
                })

            User.findByIdAndUpdate(id) {
                it.sessionExpiry = LocalDateTime.now().plusSeconds(config.sessionDurationSeconds)
                it.sessionHash = Argon2.hash(session.toCharArray())
            }

            logger.info("New session created, user: $id, aud: $aud")

            "$id:$session"
        } else {
            throw IllegalArgumentException("Invalid password for user: $id")
        }
    }

    override fun getSessionUserId(token: String): Int {
        val (subject, session) = token.split(":", limit = 2).takeIf { it.size == 2 }
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

        logger.info("Validated session token, user: $userId")

        return userId
    }


    override fun clearSession(userId: Int) {
        User.findByIdAndUpdate(userId) {
            it.sessionHash = null
        }

        logger.info("Session cleared, user: $userId")
    }

    private fun createUser(
        id: Int,
        firstName: String,
        middleName: String?,
        lastName: String?,
        password: String,
    ) = User.wrapRow(Users.insert {
        it[Users.id] = id
        it[Users.firstName] = firstName
        it[Users.middleName] = middleName
        it[Users.lastName] = lastName
        it[Users.passwordHash] = Argon2.hash(password.toCharArray())
    }.resultedValues!!.first())
}