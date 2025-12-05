package th.ac.bodin2.electives.api.db

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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

        fun getAllElectiveSelectionIds(studentId: Int): List<Pair<Int, Int>> = transaction {
            StudentElectives
                .selectAll().where { StudentElectives.student eq studentId }
                .map { it[StudentElectives.elective].value to it[StudentElectives.subject].value }
        }

        fun getAllElectiveSelections(studentId: Int): List<Pair<Elective, Subject>> = transaction {
            getAllElectiveSelectionIds(studentId).map { (electiveId, subjectId) ->
                val elective = Elective.findById(electiveId)!!
                val subject = Subject.findById(subjectId)!!
                elective to subject
            }
        }

        fun getElectiveSelectionId(studentId: Int, electiveId: Int): Int? = transaction {
            val selection = StudentElectives
                .selectAll()
                .where { (StudentElectives.student eq studentId) and (StudentElectives.elective eq electiveId) }
                .singleOrNull() ?: return@transaction null

            selection[StudentElectives.subject].value
        }

        fun getElectiveSelection(studentId: Int, electiveId: Int): Subject? = transaction {
            val subjectId = getElectiveSelectionId(studentId, electiveId) ?: return@transaction null
            Subject.findById(subjectId)!!
        }

        fun setElectiveSelection(studentId: Int, electiveId: Int, subjectId: Int) {
            transaction {
                val updated =
                    StudentElectives.update({ (StudentElectives.student eq studentId) and (StudentElectives.elective eq electiveId) }) {
                        it[subject] = subjectId
                    }

                if (updated == 0) {
                    StudentElectives.insert {
                        it[student] = studentId
                        it[elective] = electiveId
                        it[subject] = subjectId
                    }
                }
            }

            logger.info("Student elective selection, user: $studentId, elective: $electiveId, subject: $subjectId")
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
        /**
         * Removes a selection from the specified elective.
         *
         * @throws IllegalArgumentException if the selection does not exist.
         */
        fun removeElectiveSelection(studentId: Int, electiveId: Int) {
            val count = transaction {
                StudentElectives.deleteWhere {
                    (student eq studentId) and (elective eq electiveId)
                }
            }

            if (count == 0) throw IllegalArgumentException("Student elective selection does not exist")

            logger.info("Student elective deselection, user: $studentId, elective: $electiveId")
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
     * Adds a selection to the specified elective.
     */
    fun setElectiveSelection(electiveId: Int, subjectId: Int) = Student.setElectiveSelection(id, electiveId, subjectId)

    /**
     * Removes a selection from the specified elective.
     *
     * @throws IllegalArgumentException if the selection does not exist.
     */
    fun removeElectiveSelection(electiveId: Int) = Student.removeElectiveSelection(id, electiveId)

    val selections
        get() = getAllElectiveSelections(id)
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

        fun teachesSubject(teacherId: Int, subjectId: Int): Boolean = transaction {
            TeacherSubjects
                .selectAll().where { (TeacherSubjects.teacher eq teacherId) and (TeacherSubjects.subject eq subjectId) }
                .count() > 0L
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
        get() = transaction {
            (TeacherSubjects innerJoin Teachers)
                .selectAll().where { TeacherSubjects.subject eq this@Subject.id }
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