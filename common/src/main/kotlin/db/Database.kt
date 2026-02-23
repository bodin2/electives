package th.ac.bodin2.electives.db

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.db.models.*
import org.jetbrains.exposed.v1.jdbc.Database as ExposedDatabase

object Database {
    fun init(url: String, driver: String, setup: ExposedDatabase.() -> Unit): ExposedDatabase {
        val db = ExposedDatabase.connect(url, driver).apply(setup)

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