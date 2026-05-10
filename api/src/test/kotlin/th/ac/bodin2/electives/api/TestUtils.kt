package th.ac.bodin2.electives.api

import com.google.protobuf.MessageLite
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
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
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.*

val adminKeyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply {
    initialize(2048)
}.generateKeyPair()

fun setupTestEnvironment() {
    System.setProperty("ADMIN_ENABLED", "1")
    System.setProperty("ADMIN_PUBLIC_KEY", Base64.getEncoder().encodeToString(adminKeyPair.public.encoded))
    System.setProperty("APP_ENV", "test")
}

object TestDatabase {
    lateinit var db: org.jetbrains.exposed.v1.jdbc.Database
        private set

    fun connect() {
        if (!::db.isInitialized) {
            val dataSource = HikariDataSource(HikariConfig().apply {
                // SQLite in-memory shared cache so all pooled connections see the
                // same database. The pool is capped at 1 because SQLite serializes
                // writes anyway, and to avoid surprises with the in-memory store.
                jdbcUrl = "jdbc:sqlite:file:test?mode=memory&cache=shared"
                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = 1
                connectionInitSql = "PRAGMA foreign_keys=ON;"
            })
            db = org.jetbrains.exposed.v1.jdbc.Database.connect(dataSource)
        }

        TransactionManager.defaultDatabase = db
    }

    fun reset() {
        SchemaUtils.drop(*Database.tables.toTypedArray())
        SchemaUtils.create(*Database.tables.toTypedArray())
    }

    fun Application.mockData() {
        val usersService: UsersService by dependencies

        Groups.insert {
            it[id] = TestConstants.Groups.GROUP_1_ID
            it[name] = TestConstants.Groups.GROUP_1_NAME
        }
        Groups.insert {
            it[id] = TestConstants.Groups.GROUP_2_ID
            it[name] = TestConstants.Groups.GROUP_2_NAME
        }

        usersService.createStudent(
            id = TestConstants.Students.JOHN_ID,
            firstName = TestConstants.Students.JOHN_FIRST_NAME,
            middleName = TestConstants.Students.JOHN_MIDDLE_NAME,
            lastName = TestConstants.Students.JOHN_LAST_NAME,
            password = TestConstants.Students.JOHN_PASSWORD
        )
        usersService.createStudent(
            id = TestConstants.Students.JANE_ID,
            firstName = TestConstants.Students.JANE_FIRST_NAME,
            lastName = TestConstants.Students.JANE_LAST_NAME,
            password = TestConstants.Students.JANE_PASSWORD
        )

        StudentGroups.insert {
            it[student] = TestConstants.Students.JOHN_ID
            it[group] = TestConstants.Groups.GROUP_1_ID
        }
        StudentGroups.insert {
            it[student] = TestConstants.Students.JANE_ID
            it[group] = TestConstants.Groups.GROUP_2_ID
        }

        usersService.createTeacher(
            id = TestConstants.Teachers.BOB_ID,
            firstName = TestConstants.Teachers.BOB_FIRST_NAME,
            lastName = TestConstants.Teachers.BOB_LAST_NAME,
            password = TestConstants.Teachers.BOB_PASSWORD
        )
        usersService.createTeacher(
            id = TestConstants.Teachers.ALICE_ID,
            firstName = TestConstants.Teachers.ALICE_FIRST_NAME,
            middleName = TestConstants.Teachers.ALICE_MIDDLE_NAME,
            lastName = TestConstants.Teachers.ALICE_LAST_NAME,
            password = TestConstants.Teachers.ALICE_PASSWORD
        )

        Enrollments.insert {
            it[id] = TestConstants.Enrollments.SCIENCE_ID
            it[name] = TestConstants.Enrollments.SCIENCE_NAME
            it[startDate] = null
            it[endDate] = null
            it[group] = TestConstants.Groups.GROUP_1_ID
        }

        Subjects.insert {
            it[id] = TestConstants.Subjects.PHYSICS_ID
            it[name] = TestConstants.Subjects.PHYSICS_NAME
            it[description] = TestConstants.Subjects.PHYSICS_DESCRIPTION
            it[code] = TestConstants.Subjects.PHYSICS_CODE
            it[tag] = SubjectTag.SCIENCE_AND_TECHNOLOGY.number
            it[location] = TestConstants.Subjects.PHYSICS_LOCATION
            it[capacity] = TestConstants.Subjects.PHYSICS_CAPACITY
            it[group] = TestConstants.Groups.GROUP_1_ID
        }

        Subjects.insert {
            it[id] = TestConstants.Subjects.CHEMISTRY_ID
            it[name] = TestConstants.Subjects.CHEMISTRY_NAME
            it[description] = TestConstants.Subjects.CHEMISTRY_DESCRIPTION
            it[code] = TestConstants.Subjects.CHEMISTRY_CODE
            it[tag] = SubjectTag.SCIENCE_AND_TECHNOLOGY.number
            it[location] = TestConstants.Subjects.CHEMISTRY_LOCATION
            it[capacity] = TestConstants.Subjects.CHEMISTRY_CAPACITY
            it[group] = TestConstants.Groups.GROUP_1_ID
        }

        EnrollmentSubjects.insert {
            it[enrollment] = TestConstants.Enrollments.SCIENCE_ID
            it[subject] = TestConstants.Subjects.PHYSICS_ID
        }
        EnrollmentSubjects.insert {
            it[enrollment] = TestConstants.Enrollments.SCIENCE_ID
            it[subject] = TestConstants.Subjects.CHEMISTRY_ID
        }

        TeacherSubjects.insert {
            it[teacher] = TestConstants.Teachers.BOB_ID
            it[subject] = TestConstants.Subjects.PHYSICS_ID
            it[enrollment] = TestConstants.Enrollments.SCIENCE_ID
        }
        TeacherSubjects.insert {
            it[teacher] = TestConstants.Teachers.ALICE_ID
            it[subject] = TestConstants.Subjects.CHEMISTRY_ID
            it[enrollment] = TestConstants.Enrollments.SCIENCE_ID
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

suspend fun HttpClient.patchProtoWithAuth(url: String, message: MessageLite, token: String): HttpResponse {
    return patch(url) {
        bearerAuth(token)
        contentType(ContentType.Application.ProtoBuf)
        setBody(message.toByteArray())
    }
}

suspend inline fun <reified T : MessageLite> HttpResponse.parse(): T {
    val parser = getParser(T::class.java)
    return parser.parseFrom(bodyAsBytes())
}
