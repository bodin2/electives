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
import th.ac.bodin2.electives.utils.Paseto
import th.ac.bodin2.electives.utils.PasetoClaims
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class UsersServiceImpl : UsersService {
    companion object {
        private val logger = LoggerFactory.getLogger(UsersServiceImpl::class.java)
    }

//    @TODO: Config
    private val sessionDuration = 1.days

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

        if (aud.length > 256) {
            throw IllegalArgumentException("Client name too long for user: $id")
        }

        val user = Users.select(Users.passwordHash).where { Users.id eq id }.singleOrNull()
            ?: throw NotFoundException(NotFoundEntity.USER, "User does not exist: $id")

        val passwordHash = user[Users.passwordHash]

        return if (Argon2.verify(passwordHash.toByteArray(), password.toCharArray())) {
            val token = Paseto.sign(
                PasetoClaims.from(
                    iss = Paseto.ISSUER,
                    sub = id.toString(),
                    aud = aud,
                    exp = OffsetDateTime.now().plusSeconds(sessionDuration.inWholeSeconds)
                )
            )

            User.findByIdAndUpdate(id) {
                it.sessionHash = Argon2.hash(token.toCharArray())
            }

            logger.info("New session created, user: $id, aud: $aud")

            token
        } else {
            throw IllegalArgumentException("Invalid password for user: $id")
        }
    }

    override fun getSessionUserId(token: String): Int {
        val claims = Paseto.verify(token)

        val userId = claims.sub.toIntOrNull() ?: throw IllegalArgumentException("Invalid token subject: ${claims.sub}")

        val user = Users.select(Users.sessionHash).where { Users.id eq userId }.singleOrNull()
            ?: throw NotFoundException(NotFoundEntity.USER, "User does not exist: $userId")

        val sessionHash = user[Users.sessionHash]
            ?: throw IllegalArgumentException("No active session for user: $userId")

        if (!Argon2.verify(sessionHash.toByteArray(), token.toCharArray())) {
            throw IllegalArgumentException("Invalid session token for user: $userId")
        }

        logger.info("Validated session token, user: $userId, aud: ${claims.aud}")

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
    ) = User.new(id) {
        this.firstName = firstName
        this.middleName = middleName
        this.lastName = lastName
        passwordHash = Argon2.hash(password.toCharArray())
    }
}