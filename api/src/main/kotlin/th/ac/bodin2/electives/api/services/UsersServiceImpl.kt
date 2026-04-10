package th.ac.bodin2.electives.api.services

import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import io.ktor.server.plugins.di.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.dao.load
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.UsersServiceImpl.Config
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.Team
import th.ac.bodin2.electives.db.User
import th.ac.bodin2.electives.db.models.*
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.utils.Argon2
import th.ac.bodin2.electives.utils.env
import th.ac.bodin2.electives.utils.withMinimumDelay
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun DependencyRegistry.provideUsersService() = provide<UsersService> {
    UsersServiceImpl(
        Config(
            sessionDurationSeconds =
                (env("USER_SESSION_DURATION")?.toIntOrNull()?.seconds ?: 1.days).inWholeSeconds,
            minimumSessionCreationTime =
                (env("USER_SESSION_CREATION_MINIMUM_TIME")?.toIntOrNull()?.milliseconds ?: 500.milliseconds),
        ),
        argon2 = resolve<Argon2>()
    )
}

class UsersServiceImpl(val config: Config, val argon2: Argon2) : UsersService {
    class Config(val sessionDurationSeconds: Long, val minimumSessionCreationTime: Duration)

    companion object {
        private const val PAGE_SIZE = 50
        private const val TOKEN_SIZE = 32
        private val secureRand = SecureRandom()
        private val logger = LoggerFactory.getLogger(UsersServiceImpl::class.java)

        private val userInfoFields = listOf(
            Users.id,
            Users.avatarUrl,
            Users.firstName,
            Users.middleName,
            Users.lastName
        )
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
     * @throws EntityNotFoundException if the user does not exist.
     * @throws IllegalStateException if the user is neither a Student nor a Teacher.
     */
    override suspend fun getUserType(id: Int): UserType {
        userTypeCache.get(id)?.let { return@getUserType it }

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

        query ?: throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")

        userTypeCache.put(id, query)
        return query
    }

    override fun createStudent(
        id: Int,
        firstName: String,
        middleName: String?,
        lastName: String?,
        password: String,
        avatarUrl: String?,
        teams: List<Int>?,
    ): Student {
        return Student.new(id) {
            user = createUser(id, firstName, middleName, lastName, password, avatarUrl)
            if (teams != null) this.teams = Team.find { Teams.id inList teams }
        }.also { it.refresh(flush = true) }
    }

    override fun createTeacher(
        id: Int,
        firstName: String,
        middleName: String?,
        lastName: String?,
        password: String,
        avatarUrl: String?,
    ): Teacher {
        return Teacher.new(id) {
            user = createUser(id, firstName, middleName, lastName, password, avatarUrl)
        }.also { it.refresh(flush = true) }
    }

    @Transactional
    override fun deleteUser(id: Int) {
        val rows = transaction { Users.deleteWhere { Users.id eq id } }
        if (rows == 0) {
            throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")
        }

        runBlocking {
            val lastKnownType = userTypeCache.remove(id)
            logger.info("Deleted user (type: ${lastKnownType ?: "<not cached>"}): $id")
        }
    }

    @Transactional
    override fun updateStudent(id: Int, update: UsersService.StudentUpdate) {
        transaction {
            Student.assertExists(id)

            try {
                updateUser(id, update.user)
            } catch (e: NothingToUpdateException) {
                update.teams ?: throw e
            }

            update.teams?.let { teams ->
                StudentTeams.deleteWhere { StudentTeams.student eq id }
                StudentTeams.batchInsert(teams) {
                    Team.assertExists(it)

                    this[StudentTeams.student] = id
                    this[StudentTeams.team] = it
                }
            }
        }
    }

    @Transactional
    override fun updateTeacher(id: Int, update: UsersService.TeacherUpdate) {
        transaction {
            Teacher.assertExists(id)
            updateUser(id, update.user)
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

            if (it.firstDataSet.isEmpty()) throw NothingToUpdateException()
        }
    }

    @Transactional
    override fun setPassword(id: Int, newPassword: String) {
        val password = newPassword.assertPasswordRequirements()

        val rows = transaction {
            Users.update(where = { Users.id eq id }) {
                it[Users.passwordHash] = argon2.hash(password.toCharArray())
            }
        }

        if (rows == 0) throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")
    }

    override fun getTeacherById(id: Int): Teacher? = Teacher.findById(id)

    override fun getStudentById(id: Int): Student? = Student.findById(id)

    @Transactional
    override fun getStudents(page: Int): Pair<List<Student>, Long> {
        require(page >= 1) { "Page must be at least 1" }

        val offset = ((page - 1) * PAGE_SIZE).toLong()

        return transaction {
            val studentIds = (Students innerJoin Users)
                .select(Students.id)
                .orderBy(Students.id)
                .limit(PAGE_SIZE)
                .offset(offset)
                .map { it[Students.id].value }

            val count = Students.selectAll().count()

            if (studentIds.isEmpty()) return@transaction (emptyList<Student>() to count)

            ((Students innerJoin Users)
                .select(Students.columns + userInfoFields)
                .orderBy(Students.id)
                .where { Students.id inList studentIds }
                .map { Student.wrapRow(it).load(Student::teams) }) to (count)
        }
    }

    @Transactional
    override fun getTeachers(page: Int): Pair<List<Teacher>, Long> {
        require(page >= 1) { "Page must be at least 1" }

        val offset = ((page - 1) * PAGE_SIZE).toLong()
        return transaction {
            ((Teachers innerJoin Users)
                .select(Teachers.columns + userInfoFields)
                .orderBy(Teachers.id)
                .limit(PAGE_SIZE)
                .offset(offset)
                .map { Teacher.wrapRow(it) }) to (Teachers.selectAll().count())
        }
    }

    @Transactional
    override suspend fun createSession(id: Int, password: String, aud: String): String =
        withMinimumDelay(config.minimumSessionCreationTime) {
            val password = password.assertPasswordRequirements()

            val aud = aud.trim().apply {
                require(!isEmpty()) { "Audience blank for user: $id" }
                require(length <= 256) { "Audience string too long for user: $id (aud = ${slice(0..32)}...)" }
            }

            transaction {
                val user = Users.select(Users.passwordHash).where { Users.id eq id }.singleOrNull()
                    ?: throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")

                val passwordHash = user[Users.passwordHash]

                require(
                    argon2.verify(passwordHash, password.toCharArray())
                ) { "Invalid password for user: $id" }

                val token = insecurelyCreateSessionWithoutValidation(id)

                logger.debug("New session created, user: $id, aud: $aud")

                token
            }
        }

    @Transactional
    override fun insecurelyCreateSessionWithoutValidation(id: Int): String {
        val session = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(ByteArray(TOKEN_SIZE).apply { secureRand.nextBytes(this) })

        Users.update({ Users.id eq id }) {
            it[Users.sessionExpiry] = LocalDateTime.now().plusSeconds(config.sessionDurationSeconds)
            it[Users.sessionHash] = argon2.hash(session.toCharArray())
        }

        return "$id.$session"
    }

    override fun getSessionUserId(token: String): Int {
        val (subject, session) = token.split(".", limit = 2).takeIf { it.size == 2 }
            ?: throw IllegalArgumentException("Invalid session token format")

        val userId = subject.toIntOrNull() ?: throw IllegalArgumentException("Invalid token subject: $subject")

        val user = Users.select(Users.sessionHash, Users.sessionExpiry).where { Users.id eq userId }.singleOrNull()
            ?: throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $userId")

        val sessionExpiry = user[Users.sessionExpiry]
            ?: throw IllegalArgumentException("No active session for user: $userId")

        val sessionHash = user[Users.sessionHash]
            ?: throw IllegalArgumentException("No active session for user: $userId")

        if (sessionExpiry.isBefore(LocalDateTime.now()))
            throw IllegalArgumentException("Session expired for user: $userId")

        if (!argon2.verify(sessionHash, session.toCharArray()))
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
    ): User {
        val password = password.assertPasswordRequirements()

        val stmt = Users.insertIgnore {
            it[Users.id] = id
            it[Users.firstName] = firstName
            it[Users.middleName] = middleName
            it[Users.lastName] = lastName
            it[Users.passwordHash] = argon2.hash(password.toCharArray())
            it[Users.avatarUrl] = avatarUrl
        }

        if (stmt.insertedCount == 0) throw ConflictException(ExceptionEntity.USER)
        return User.wrapRow(stmt.resultedValues!!.first())
    }

    private fun String.assertPasswordRequirements(): String {
        val pwd = this.trim()

        require(pwd.length >= 4) { "Password must be at least 4 characters once trimmed" }
        require(pwd.length <= 4096) { "Password must have less or equal to 4096 characters" }

        return pwd
    }
}