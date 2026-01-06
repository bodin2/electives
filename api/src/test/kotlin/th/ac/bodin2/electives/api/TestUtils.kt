package th.ac.bodin2.electives.api

import com.google.protobuf.MessageLite
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.getParser
import th.ac.bodin2.electives.db.Database
import th.ac.bodin2.electives.db.models.*
import th.ac.bodin2.electives.proto.api.SubjectTag
import th.ac.bodin2.electives.utils.Argon2

fun setupTestEnvironment() {
    System.setProperty("APP_ENV", "test")
    Argon2.init(memory = 16384, iterations = 10)
}

object TestDatabase {
    lateinit var db: org.jetbrains.exposed.v1.jdbc.Database
        private set

    fun connect() {
        if (!::db.isInitialized) {
            db = org.jetbrains.exposed.v1.jdbc.Database.connect(
                "jdbc:sqlite:file:./build/tmp/test/db",
                "org.sqlite.JDBC"
            )
        }

        TransactionManager.defaultDatabase = db
    }

    fun reset() {
        SchemaUtils.drop(*Database.tables.toTypedArray())
        SchemaUtils.create(*Database.tables.toTypedArray())
    }

    fun Application.mockData() {
        val usersService: UsersService by dependencies

        Teams.insert {
            it[id] = TestConstants.Teams.TEAM_1_ID
            it[name] = TestConstants.Teams.TEAM_1_NAME
        }
        Teams.insert {
            it[id] = TestConstants.Teams.TEAM_2_ID
            it[name] = TestConstants.Teams.TEAM_2_NAME
        }

        usersService.createStudent(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_FIRST_NAME,
            TestConstants.Students.JOHN_MIDDLE_NAME,
            TestConstants.Students.JOHN_LAST_NAME,
            TestConstants.Students.JOHN_PASSWORD
        )
        usersService.createStudent(
            TestConstants.Students.JANE_ID,
            TestConstants.Students.JANE_FIRST_NAME,
            null,
            TestConstants.Students.JANE_LAST_NAME,
            TestConstants.Students.JANE_PASSWORD
        )

        StudentTeams.insert {
            it[student] = TestConstants.Students.JOHN_ID
            it[team] = TestConstants.Teams.TEAM_1_ID
        }
        StudentTeams.insert {
            it[student] = TestConstants.Students.JANE_ID
            it[team] = TestConstants.Teams.TEAM_2_ID
        }

        usersService.createTeacher(
            TestConstants.Teachers.BOB_ID,
            TestConstants.Teachers.BOB_FIRST_NAME,
            null,
            TestConstants.Teachers.BOB_LAST_NAME,
            TestConstants.Teachers.BOB_PASSWORD
        )
        usersService.createTeacher(
            TestConstants.Teachers.ALICE_ID,
            TestConstants.Teachers.ALICE_FIRST_NAME,
            TestConstants.Teachers.ALICE_MIDDLE_NAME,
            TestConstants.Teachers.ALICE_LAST_NAME,
            TestConstants.Teachers.ALICE_PASSWORD
        )

        Electives.insert {
            it[id] = TestConstants.Electives.SCIENCE_ID
            it[name] = TestConstants.Electives.SCIENCE_NAME
            it[startDate] = null
            it[endDate] = null
            it[team] = TestConstants.Teams.TEAM_1_ID
        }

        Subjects.insert {
            it[id] = TestConstants.Subjects.PHYSICS_ID
            it[name] = TestConstants.Subjects.PHYSICS_NAME
            it[description] = TestConstants.Subjects.PHYSICS_DESCRIPTION
            it[code] = TestConstants.Subjects.PHYSICS_CODE
            it[tag] = SubjectTag.SCIENCE_AND_TECHNOLOGY.number
            it[location] = TestConstants.Subjects.PHYSICS_LOCATION
            it[capacity] = TestConstants.Subjects.PHYSICS_CAPACITY
            it[team] = TestConstants.Teams.TEAM_1_ID
        }

        Subjects.insert {
            it[id] = TestConstants.Subjects.CHEMISTRY_ID
            it[name] = TestConstants.Subjects.CHEMISTRY_NAME
            it[description] = TestConstants.Subjects.CHEMISTRY_DESCRIPTION
            it[code] = TestConstants.Subjects.CHEMISTRY_CODE
            it[tag] = SubjectTag.SCIENCE_AND_TECHNOLOGY.number
            it[location] = TestConstants.Subjects.CHEMISTRY_LOCATION
            it[capacity] = TestConstants.Subjects.CHEMISTRY_CAPACITY
            it[team] = TestConstants.Teams.TEAM_1_ID
        }

        ElectiveSubjects.insert {
            it[elective] = TestConstants.Electives.SCIENCE_ID
            it[subject] = TestConstants.Subjects.PHYSICS_ID
        }
        ElectiveSubjects.insert {
            it[elective] = TestConstants.Electives.SCIENCE_ID
            it[subject] = TestConstants.Subjects.CHEMISTRY_ID
        }

        TeacherSubjects.insert {
            it[teacher] = TestConstants.Teachers.BOB_ID
            it[subject] = TestConstants.Subjects.PHYSICS_ID
        }
        TeacherSubjects.insert {
            it[teacher] = TestConstants.Teachers.ALICE_ID
            it[subject] = TestConstants.Subjects.CHEMISTRY_ID
        }
    }
}


suspend fun HttpClient.postProto(url: String, message: MessageLite): HttpResponse {
    return post(url) {
        contentType(ContentType.Application.ProtoBuf)
        setBody(message.toByteArray())
    }
}

suspend fun HttpClient.putProto(url: String, message: MessageLite): HttpResponse {
    return put(url) {
        contentType(ContentType.Application.ProtoBuf)
        setBody(message.toByteArray())
    }
}

suspend fun HttpClient.getWithAuth(url: String, token: String): HttpResponse {
    return get(url) {
        bearerAuth(token)
    }
}

suspend fun HttpClient.postProtoWithAuth(url: String, message: MessageLite, token: String): HttpResponse {
    return post(url) {
        bearerAuth(token)
        contentType(ContentType.Application.ProtoBuf)
        setBody(message.toByteArray())
    }
}

suspend fun HttpClient.putProtoWithAuth(url: String, message: MessageLite, token: String): HttpResponse {
    return put(url) {
        bearerAuth(token)
        contentType(ContentType.Application.ProtoBuf)
        setBody(message.toByteArray())
    }
}

suspend fun HttpClient.deleteWithAuth(url: String, token: String): HttpResponse {
    return delete(url) {
        bearerAuth(token)
    }
}

suspend inline fun <reified T : MessageLite> HttpResponse.parse(): T {
    val parser = getParser(T::class.java)
    return parser.parseFrom(bodyAsBytes())
}