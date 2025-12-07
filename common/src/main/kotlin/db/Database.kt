package th.ac.bodin2.electives.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import th.ac.bodin2.electives.db.models.*
import th.ac.bodin2.electives.utils.getEnv

object Database {
    private const val DEFAULT_PATH = "data.db"
    val path = getEnv("DB_PATH") ?: DEFAULT_PATH

    fun init() {
        Database.connect("jdbc:sqlite:$path", driver = "org.sqlite.JDBC")

        // Create tables if they do not exist
        transaction {
            SchemaUtils.create(
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
    }
}