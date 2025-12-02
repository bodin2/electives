package th.ac.bodin2.electives.api.db

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.api.db.models.*

class User(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, User>(Users)

    var firstName by Users.firstName
    var middleName by Users.middleName
    var lastName by Users.lastName

    var passwordHash by Users.passwordHash
    var sessionHash by Users.sessionHash
}

data class Student(val id: Int, val user: User) {
    companion object {
        private val logger = LoggerFactory.getLogger(Student::class.java)

        fun findById(id: Int): Student? = transaction {
            // Verify that the student exists
            if (Students
                    .selectAll().where { Students.id eq id }
                    .count() == 0L
            ) return@transaction null

            val user = User.findById(id) ?: return@transaction null
            Student(id, user)
        }

        fun getSelections(studentId: Int): List<Pair<Elective, Subject>> = transaction {
            (StudentElectives innerJoin Subjects)
                .selectAll().where { StudentElectives.student eq studentId }
                .map { Elective.wrapRow(it) to Subject.wrapRow(it) }
        }

        fun new(id: Int, user: User): Student {
            transaction {
                Students.insert {
                    it[Students.id] = id
                }
            }

            return Student(id, user)
        }
    }

    /**
     * Adds a subject to the student's list of subjects.
     */
    fun selectElectiveSubject(electiveId: Int, subjectId: Int) {
        transaction {
            StudentElectives.insert {
                it[student] = this@Student.id
                it[elective] = electiveId
                it[subject] = subjectId
            }
        }

        logger.info("Student elective selection, user: $id, elective: $electiveId, subject: $subjectId")
    }

    /**
     * Removes a subject from the student's list of subjects.
     */
    fun deselectElectiveSubject(electiveId: Int) {
        transaction {
            StudentElectives.deleteWhere {
                (student eq this@Student.id) and (elective eq electiveId)
            }
        }

        logger.info("Student elective deselection, user: $id, elective: $electiveId")
    }

    val selections
        get() = getSelections(id)
}

class Teacher(val id: Int, val user: User, val avatar: ByteArray?) {
    companion object {
        fun findById(id: Int): Teacher? = transaction {
            val teacher = Teachers
                .selectAll().where { Teachers.id eq id }
                .singleOrNull()
                ?: return@transaction null

            val user = User.findById(id) ?: return@transaction null
            Teacher(id, user, teacher[Teachers.avatar]?.bytes)
        }

        fun getSubjects(teacherId: Int): List<Subject> = transaction {
            (TeacherSubjects innerJoin Subjects)
                .selectAll().where { TeacherSubjects.teacher eq teacherId }
                .map { Subject.wrapRow(it) }
        }

        fun new(id: Int, user: User, avatar: ByteArray? = null): Teacher {
            transaction {
                Teachers.insert {
                    it[Teachers.id] = id
                    it[Teachers.avatar] = avatar?.let { bytes -> ExposedBlob(bytes) }
                }
            }

            return Teacher(id, user, avatar)
        }
    }

    val subjects
        get() = getSubjects(id)
}

class Team(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, Team>(Teams)

    var name by Teams.name
}

class Elective(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, Elective>(Electives)

    var name by Electives.name
    val team by Team optionalReferencedOn Electives.team

    val subjects by Subject via ElectiveSubjects

    var startDate by Electives.startDate
    var endDate by Electives.endDate
}

class Subject(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, Subject>(Subjects)

    var name by Subjects.name

    val teams by Team via SubjectTeams

    val teachers
        get() = run {
            val subjectId = id

            transaction {
                (TeacherSubjects innerJoin Teachers)
                    .selectAll().where { TeacherSubjects.subject eq subjectId }
                    .map {
                        val teacherId = it[Teachers.id].value
                        Teacher.findById(teacherId)!!
                    }
            }
        }

    var description by Subjects.description
    var code by Subjects.code
    var tag by Subjects.tag

    var location by Subjects.location
    var capacity by Subjects.capacity
}