package th.ac.bodin2.electives.api.routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.db.models.Students

fun Application.registerDevRoutes() {
    routing {
        get("/") {
            call.respondText("Electives API is running!")
        }

        get("/dev/ping") {
            call.respondText("pong")
        }

        get("/dev/addStudent") {
            if (transaction { Students.selectAll().where { Students.id eq 12345 }.count() } > 0L) {
                transaction { Students.deleteWhere { Students.id eq 12345 } }
            }

            UsersService.createStudent(
                id = 12345,
                password = "password1234",
                firstName = "John",
                lastName = "Doe"
            )
        }

        get("/dev/getAddedStudent") {
            val student = UsersService.getStudentById(12345)!!
            call.respondText("Student: ${student.user.firstName} ${student.user.lastName}, ID: ${student.id}")
        }

        get("/dev/createSession") {
            val token = UsersService.createSession(12345, "password1234", "api-test")
            call.respondText(token)
        }

        post("/dev/session") {
            val token = call.receive<String>()
            call.respondText(UsersService.getSessionUserId(token).toString())
        }
    }
}