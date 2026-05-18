package th.ac.bodin2.electives.db

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.db.models.*
import javax.sql.DataSource
import org.jetbrains.exposed.v1.jdbc.Database as ExposedDatabase

object Database {
    fun init(dataSource: DataSource): ExposedDatabase {
        val db = ExposedDatabase.connect(dataSource)

        // Create tables if they do not exist
        transaction { SchemaUtils.create(*tables.toTypedArray()) }

        return db
    }

    val tables = listOf(
        Groups,
        Users,
        Enrollments,
        EnrollmentSubjects,
        StudentClasses,
        Students,
        StudentGroups,
        Subjects,
        Teachers,
        TeacherSubjects,
        TeacherGroups,
        Admins,
    )
}
