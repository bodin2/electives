package th.ac.bodin2.electives.api.services

import io.ktor.server.plugins.di.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.dao.load
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.UsersServiceImpl.Config
import th.ac.bodin2.electives.db.*
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

    private val _sessionCreationFlow = MutableSharedFlow<Int>()
    override val sessionCreationFlow: SharedFlow<Int> = _sessionCreationFlow.asSharedFlow()

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

    override fun getUserType(id: Int): UserType {
        val query = Users
            .leftJoin(Students)
            .leftJoin(Teachers)
            .leftJoin(Admins)
            .select(Users.id, Students.id, Teachers.id, Admins.id)
            .where { Users.id eq id }
            .firstOrNull()

        query ?: throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")

        return when {
            query.getOrNull(Students.id) != null -> UserType.STUDENT
            query.getOrNull(Teachers.id) != null -> UserType.TEACHER
            query.getOrNull(Admins.id) != null -> UserType.ADMIN
            else -> throw IllegalStateException("User is not a Student, Teacher, or Admin: $id")
        }
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
        val user = createUser(id, firstName, middleName, lastName, password, avatarUrl)
        val studentRow = Students
            .insert {
                it[Students.id] = user.id
                it[Students.user] = user.id
            }
            .resultedValues!!
            .first()

        StudentTeams.batchInsert(teams ?: emptyList()) {
            Team.assertExists(it)

            this[StudentTeams.student] = id
            this[StudentTeams.team] = it
        }

        return Student.wrapRow(studentRow).load(Student::teams)
    }

    @Transactional
    override fun createStudents(inserts: List<UsersService.StudentInsert>) = transaction {
        if (inserts.isEmpty()) return@transaction emptyList()

        val requestedTeamIds = inserts
            .flatMap { it.teams }
            .toSet()

        val existingTeamIds = Teams
            .select(Teams.id)
            .where { Teams.id inList requestedTeamIds.toList() }
            .map { it[Teams.id].value }
            .toSet()

        val missingTeamIds = requestedTeamIds.filter { it !in existingTeamIds }
        if (!missingTeamIds.isEmpty()) throw UsersService.BatchOperationException.MissingTeams(missingTeamIds)

        val prepared = insertUserBatch(inserts)

        Students.batchInsert(prepared) { item ->
            val uid = item.request.user.id

            this[Students.user] = uid
            this[Students.id] = uid
        }

        val studentTeams = prepared.flatMap { item ->
            item.request.teams
                .distinct()
                .map { teamId -> item.request.user.id to teamId }
        }

        StudentTeams.batchInsert(studentTeams) { (studentId, teamId) ->
            this[StudentTeams.student] = studentId
            this[StudentTeams.team] = teamId
        }

        return@transaction Student.find { Students.id inList prepared.map { it.request.user.id } }
            .with(Student::user, Student::teams)
            .toList()
    }

    private data class PreparedUserInsert<T>(
        val request: T,
        val passwordHash: String
    )

    override fun createTeacher(
        id: Int,
        firstName: String,
        middleName: String?,
        lastName: String?,
        password: String,
        avatarUrl: String?,
    ) = Teacher.new(id) {
        user = createUser(id, firstName, middleName, lastName, password, avatarUrl)
    }

    @Transactional
    override fun createTeachers(inserts: List<UsersService.TeacherInsert>) = transaction {
        if (inserts.isEmpty()) return@transaction emptyList()

        val prepared = insertUserBatch(inserts)

        Teachers.batchInsert(prepared) { item ->
            val uid = item.request.user.id

            this[Teachers.user] = uid
            this[Teachers.id] = uid
        }

        return@transaction Teacher.find { Teachers.id inList prepared.map { it.request.user.id } }
            .with(Teacher::user)
            .toList()
    }

    // @TODO: Test this
    @Transactional
    override fun createAdmin(insert: UsersService.AdminInsert) = transaction {
        Admin.new(insert.user.id) {
            user = createUser(
                id = insert.user.id,
                firstName = insert.user.firstName,
                middleName = insert.user.middleName,
                lastName = insert.user.lastName,
                password = insert.user.password,
                avatarUrl = insert.user.avatarUrl,
                assertPassword = false
            )

            publicKey = Base64.getEncoder().encodeToString(insert.publicKey.encoded)
        }
    }

    private fun <T : UsersService.UserInsert> insertUserBatch(inserts: List<T>): List<PreparedUserInsert<T>> {
        val conflicts = Users
            .select(Users.id)
            .where { Users.id inList inserts.map { it.user.id } }
            .map { it[Users.id].value }
            .toList()

        if (conflicts.isNotEmpty()) {
            throw UsersService.BatchOperationException.ConflictingEntities(conflicts)
        }

        val prepared = inserts.map { req ->
            val user = req.user
            val passwordHash = try {
                argon2.hash(user.password.assertPasswordRequirements().toCharArray())
            } catch (e: IllegalArgumentException) {
                throw UsersService.BatchOperationException.InvalidUserData(user.id, e)
            }

            PreparedUserInsert(
                request = req,
                passwordHash = passwordHash
            )
        }

        Users.batchInsert(prepared) { item ->
            val user = item.request.user

            this[Users.id] = user.id
            this[Users.avatarUrl] = user.avatarUrl
            this[Users.firstName] = user.firstName
            this[Users.middleName] = user.middleName
            this[Users.lastName] = user.lastName
            this[Users.passwordHash] = item.passwordHash
        }

        return prepared
    }

    @Transactional
    override fun deleteUser(id: Int) {
        val rows = transaction { Users.deleteWhere { Users.id eq id } }
        if (rows == 0) {
            throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")
        }

        logger.info("Deleted user: $id")
    }

    @Transactional
    override suspend fun deleteUsers(id: List<Int>) {
        if (id.isEmpty()) return

        suspendTransaction {
            val notFoundIds = Users
                .select(Users.id)
                .where { not(Users.id inList id) }
                .map { it[Users.id].value }
                .toList()

            if (notFoundIds.isNotEmpty()) {
                throw UsersService.BatchOperationException.NotFoundEntities(notFoundIds)
            }

            Users.deleteWhere { Users.id inList id }
        }

        logger.info("Deleted users: ${id.joinToString(", ")}")
    }

    @Transactional
    override fun updateStudent(id: Int, update: UsersService.StudentUpdate) = transaction {
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

        Student.findById(id)!!.load(Student::user, Student::teams)
    }

    @Transactional
    override fun updateTeacher(id: Int, update: UsersService.TeacherUpdate) = transaction {
        Teacher.assertExists(id)
        updateUser(id, update.user)
        Teacher.findById(id)!!.load(Teacher::user)
    }

    private fun updateUser(id: Int, update: UsersService.UserUpdate) {
        Users.update(where = { Users.id eq id }) {
            with(update) {
                if (firstName != null) it[Users.firstName] = firstName
                if (setMiddleName) it[Users.middleName] = middleName
                if (setLastName) it[Users.lastName] = lastName
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

    override fun getAdminById(id: Int): Admin? = Admin.findById(id)

    @Transactional
    override fun getStudents(page: Int): Pair<List<Student>, Long> {
        require(page >= 1) { "Page must be at least 1" }
        val offset = ((page - 1) * PAGE_SIZE).toLong()
        return transaction {
            val query = Students.selectAll()
                .orderBy(Students.id)
                .limit(PAGE_SIZE)
                .offset(offset)

            val students = Student.wrapRows(query)
                .with(Student::user, Student::teams)
                .toList()

            students to Students.selectAll().count()
        }
    }

    @Transactional
    override fun getTeachers(page: Int): Pair<List<Teacher>, Long> {
        require(page >= 1) { "Page must be at least 1" }

        val offset = ((page - 1) * PAGE_SIZE).toLong()
        return transaction {
            val query = Teachers.selectAll()
                .orderBy(Teachers.id)
                .limit(PAGE_SIZE)
                .offset(offset)

            val teachers = Teacher.wrapRows(query)
                .with(Teacher::user)
                .toList()

            teachers to Teachers.selectAll().count()
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

            suspendTransaction {
                val user = Users.select(Users.passwordHash).where { Users.id eq id }.singleOrNull()
                    ?: throw EntityNotFoundException(ExceptionEntity.USER, "User does not exist: $id")

                val passwordHash = user[Users.passwordHash]

                require(passwordHash != null) { "User not password authenticatable: $id" }

                require(
                    argon2.verify(passwordHash, password.toCharArray())
                ) { "Invalid password for user: $id" }

                val token = insecurelyCreateSessionWithoutValidation(id)

                _sessionCreationFlow.emit(id)
                logger.debug("New session created, user: $id, aud: $aud")

                token
            }
        }

    @Transactional
    override fun insecurelyCreateSessionWithoutValidation(id: Int, customDurationSeconds: Long?): String {
        val session = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(ByteArray(TOKEN_SIZE).apply { secureRand.nextBytes(this) })

        Users.update({ Users.id eq id }) {
            it[Users.sessionExpiry] =
                LocalDateTime.now().plusSeconds(customDurationSeconds ?: config.sessionDurationSeconds)

            it[Users.sessionHash] = argon2.hash(session.toCharArray())
        }

        return "$id.$session"
    }

    override fun getSessionUser(token: String): UsersService.SessionUser {
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

        return UsersService.SessionUser(userId, getUserType(userId))
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
        assertPassword: Boolean = true
    ): User {
        val password = if (assertPassword) password.assertPasswordRequirements() else password

        val stmt = Users.insertIgnore {
            it[Users.id] = id
            it[Users.firstName] = firstName
            it[Users.middleName] = middleName
            it[Users.lastName] = lastName
            it[Users.passwordHash] = if (password.isBlank()) null else argon2.hash(password.toCharArray())
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