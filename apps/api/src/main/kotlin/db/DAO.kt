package th.ac.bodin2.electives.api.db

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.api.NotFoundException
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
         * Removes a selection from the specified elective.
         *
         * @throws NotFoundException if the selection does not exist.
         */
        fun removeElectiveSelection(studentId: Int, electiveId: Int) {
            val count = transaction {
                StudentElectives.deleteWhere {
                    (student eq studentId) and (elective eq electiveId)
                }
            }

            if (count == 0) throw NotFoundException("Student elective selection does not exist")

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
    fun setElectiveSelection(electiveId: Int, subjectId: Int) = setElectiveSelection(id, electiveId, subjectId)

    /**
     * Removes a selection from the specified elective.
     *
     * @throws NotFoundException if the selection does not exist.
     */
    fun removeElectiveSelection(electiveId: Int) = removeElectiveSelection(id, electiveId)

    val teams
        get() = transaction {
            (StudentTeams innerJoin Teams)
                .selectAll().where { StudentTeams.student eq this@Student.id }
                .map { Team.wrapRow(it) }
        }

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

open class ElectiveCompanion : EntityClass<Int, Elective>(Electives) {
    fun exists(id: Int): Boolean = transaction {
        Electives
            .selectAll().where { Electives.id eq id }
            .count() > 0L
    }

    /**
     * Gets the subjects for the specified elective.
     *
     * @throws NotFoundException if the elective does not exist.
     */
    fun getSubjects(electiveId: Int): List<Subject> {
        if (!exists(electiveId)) throw NotFoundException("Elective does not exist")

        return transaction {
            (ElectiveSubjects innerJoin Subjects)
                .selectAll().where { ElectiveSubjects.elective eq electiveId }
                .map { Subject.wrapRow(it) }
        }
    }

    fun getSubjectsEnrolledCounts(electiveId: Int): Map<Int, Int> {
        if (!exists(electiveId)) throw NotFoundException("Elective does not exist")

        return transaction {
            val subjectIds = (ElectiveSubjects innerJoin Subjects)
                .selectAll().where { ElectiveSubjects.elective eq electiveId }
                .map { it[Subjects.id].value }

            val counts = mutableMapOf<Int, Int>()
            subjectIds.forEach { subjectId ->
                val count = StudentElectives
                    .selectAll().where { StudentElectives.subject eq subjectId }
                    .count().toInt()

                counts[subjectId] = count
            }

            counts
        }
    }
}

class Elective(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : ElectiveCompanion()

    var name by Electives.name
    val team by Team optionalReferencedOn Electives.team

    val subjects by Subject via ElectiveSubjects

    var startDate by Electives.startDate
    var endDate by Electives.endDate
}

open class SubjectCompanion : EntityClass<Int, Subject>(Subjects) {
    fun exists(id: Int): Boolean = transaction {
        Subjects
            .selectAll().where { Subjects.id eq id }
            .count() > 0L
    }

    fun getTeachers(subjectId: Int, electiveId: Int): List<Teacher> {
        if (!exists(subjectId)) throw NotFoundException("Subject does not exist")

        return transaction {
            (TeacherSubjects innerJoin Teachers)
                .selectAll()
                .where { (TeacherSubjects.subject eq subjectId) and (TeacherSubjects.elective eq electiveId) }
                .map {
                    val teacherId = it[Teachers.id].value
                    Teacher.findById(teacherId)!!
                }
        }
    }

    fun getStudents(subjectId: Int, electiveId: Int): List<Student> {
        if (!exists(subjectId)) throw NotFoundException("Subject does not exist")

        return transaction {
            (StudentElectives innerJoin Students)
                .selectAll()
                .where { (StudentElectives.subject eq subjectId) and (StudentElectives.elective eq electiveId) }
                .map {
                    val studentId = it[Students.id].value
                    Student.findById(studentId)!!
                }
        }
    }

    fun getEnrolledCount(subjectId: Int, electiveId: Int): Int {
        if (!exists(subjectId)) throw NotFoundException("Subject does not exist")

        return transaction {
            val subjectExists = (ElectiveSubjects innerJoin Subjects)
                .selectAll().where {
                    (ElectiveSubjects.elective eq electiveId) and (ElectiveSubjects.subject eq subjectId)
                }
                .count() > 0L

            if (!subjectExists) throw NotFoundException("Subject is not part of the elective")

            StudentElectives
                .selectAll()
                .where { (StudentElectives.subject eq subjectId) and (StudentElectives.elective eq electiveId) }
                .count().toInt()
        }
    }
}

class Subject(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : SubjectCompanion()

    var name by Subjects.name

    val teams by Team via SubjectTeams

    fun getTeachers(electiveId: Int) = getTeachers(id.value, electiveId)
    fun getStudents(electiveId: Int) = getStudents(id.value, electiveId)
    fun getEnrolledCount(electiveId: Int) = getEnrolledCount(id.value, electiveId)

    var description by Subjects.description
    var code by Subjects.code
    var tag by Subjects.tag

    var location by Subjects.location
    var capacity by Subjects.capacity
}