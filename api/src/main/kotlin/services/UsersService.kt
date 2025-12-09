package th.ac.bodin2.electives.api.services

import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.services.UsersService.userTypeCache
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
import kotlin.time.Duration.Companion.minutes

private val logger = LoggerFactory.getLogger(UsersService::class.java)

typealias SubjectSelectionUpdateListener = (enrolledCount: Int) -> Unit

object UsersService {
    private const val SESSION_DURATION_DAYS = 1L

    // 4 MB cache for user types
    private val userTypeCache = InMemoryKache<Int, UserType>(maxSize = 4 * 1024 * 1024) {
        strategy = KacheStrategy.LRU
        expireAfterWriteDuration = 10.minutes
    }


    private val subjectSelectionSubscriptions =
        mutableMapOf<Int, MutableMap<Int, MutableList<SubjectSelectionUpdateListener>>>()

    /**
     * Subscribes to elective selection updates for the given elective ID and subject ID.
     *
     * @param electiveId The ID of the elective.
     */
    fun subscribeToSubjectSelections(electiveId: Int, subjectId: Int, listener: SubjectSelectionUpdateListener) {
        val subjectSubscriptions = subjectSelectionSubscriptions.getOrPut(electiveId) { mutableMapOf() }
        val listeners = subjectSubscriptions.getOrPut(subjectId) { mutableListOf() }
        listeners.add(listener)
    }

    fun unsubscribeFromSubjectSelections(electiveId: Int, subjectId: Int, listener: SubjectSelectionUpdateListener) {
        subjectSelectionSubscriptions[electiveId]?.get(subjectId)?.remove(listener)
    }

    /**
     * Gets the [UserType] of the given user ID.
     *
     * Values are LRU-cached with TTL after each database read, see [userTypeCache].
     *
     * @throws NotFoundException if the user does not exist.
     * @throws IllegalStateException if the user is neither a Student nor a Teacher.
     */
    suspend fun getUserType(id: Int): UserType {
        userTypeCache.get(id)?.let { return it }

        val query = transaction {
            Users
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
        }

        query ?: throw NotFoundException("User does not exist: $id")

        userTypeCache.put(id, query)
        return query
    }

    fun createStudent(
        id: Int,
        firstName: String,
        middleName: String? = null,
        lastName: String? = null,
        password: String,
    ): Student = transaction {
        Student.new(id, createUser(id, firstName, middleName, lastName, password))
    }

    fun createTeacher(
        id: Int,
        firstName: String,
        middleName: String? = null,
        lastName: String? = null,
        password: String,
        avatar: ByteArray? = null,
    ): Teacher = transaction {
        Teacher.new(id, createUser(id, firstName, middleName, lastName, password), avatar)
    }

    fun getTeacherById(id: Int): Teacher? = Teacher.findById(id)

    fun getStudentById(id: Int): Student? = Student.findById(id)

    /**
     * Creates a session for the user with the given ID and password.
     *
     * @throws NotFoundException if the user does not exist.
     * @throws IllegalArgumentException if the password is invalid.
     */
    fun createSession(id: Int, password: String, aud: String): String {
        val user = transaction { Users.select(Users.passwordHash).where { Users.id eq id }.singleOrNull() }
            ?: throw NotFoundException("User does not exist: $id")

        val passwordHash = user[Users.passwordHash]

        return if (Argon2.verify(passwordHash.toByteArray(), password.toCharArray())) {
            val token = Paseto.sign(
                PasetoClaims.from(
                    iss = Paseto.ISSUER,
                    sub = id.toString(),
                    aud = aud,
                    exp = OffsetDateTime.now().plusDays(SESSION_DURATION_DAYS)
                )
            )

            transaction {
                User.findByIdAndUpdate(id) {
                    it.sessionHash = Argon2.hash(token.toCharArray())
                }
            }

            logger.info("New session created, user: $id, aud: $aud")

            token
        } else {
            throw IllegalArgumentException("Invalid password for user: $id")
        }
    }

    /**
     * Gets the user ID associated with the given session token.
     *
     * @throws NotFoundException if the user does not exist.
     * @throws IllegalArgumentException if the token or session is invalid.
     */
    fun getSessionUserId(token: String): Int {
        val claims = Paseto.verify(token)

        val userId = claims.sub.toIntOrNull() ?: throw IllegalArgumentException("Invalid token subject: ${claims.sub}")

        val user = transaction { Users.select(Users.sessionHash).where { Users.id eq userId }.singleOrNull() }
            ?: throw NotFoundException("User does not exist: $userId")

        val sessionHash =
            user[Users.sessionHash] ?: throw IllegalArgumentException("No active session for user: $userId")

        if (!Argon2.verify(sessionHash.toByteArray(), token.toCharArray())) {
            throw IllegalArgumentException("Invalid session token for user: $userId")
        }

        logger.info("Validated session token, user: $userId, aud: ${claims.aud}")

        return userId
    }

    /**
     * Clears the session (logs out) for the user with the given ID.
     */
    fun clearSession(userId: Int) {
        transaction {
            User.findByIdAndUpdate(userId) {
                it.sessionHash = null
            }
        }

        logger.info("Session cleared, user: $userId")
    }

    private fun createUser(
        id: Int,
        firstName: String,
        middleName: String?,
        lastName: String?,
        password: String,
    ) = transaction {
        User.new(id) {
            this.firstName = firstName
            this.middleName = middleName
            this.lastName = lastName
            passwordHash = Argon2.hash(password.toCharArray())
        }
    }
}