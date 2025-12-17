package th.ac.bodin2.electives.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import th.ac.bodin2.electives.db.models.*

object Database {
    fun init(url: String, driver: String): Database {
        val db = Database.connect(url, driver)

        // Create tables if they do not exist
        transaction { SchemaUtils.create(*tables.toTypedArray()) }

        return db
    }

    val tables = listOf(
        Electives,
        ElectiveSubjects,
        StudentElectives,
        Students,
        StudentTeams,
        Subjects,
        Teachers,
        TeacherSubjects,
        Teams,
        Users,
    )
}