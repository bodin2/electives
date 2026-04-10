package th.ac.bodin2.electives.db

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.dao.load
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.db.models.*
import java.time.LocalDateTime

class User(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, User>(Users)

    var firstName by Users.firstName
    var middleName by Users.middleName
    var lastName by Users.lastName

    var avatarUrl by Users.avatarUrl
}

class Student(id: EntityID<Int>) : Entity<Int>(id) {
    var user by User referencedOn Students.user
    var teams by Team via StudentTeams

    companion object : EntityClass<Int, Student>(Students) {
        fun assertExists(studentId: Int) {
            if (!exists(studentId)) throw EntityNotFoundException(ExceptionEntity.STUDENT)
        }

        fun hasTeam(studentId: Int, teamId: EntityID<Int>) = hasTeam(studentId, teamId.value)
        fun hasTeam(studentId: Int, teamId: Int): Boolean {
            return StudentTeams
                .selectAll().where { (StudentTeams.student eq studentId) and (StudentTeams.team eq teamId) }
                .empty().not()
        }

        fun getAllElectiveSelections(studentId: Int): List<Pair<Int, Subject>> {
            return (StudentElectives innerJoin Subjects)
                .selectAll().where { StudentElectives.student eq studentId }
                .map {
                    val electiveId = it[StudentElectives.elective]
                    electiveId.value to Subject.wrapRow(it)
                }
        }

        fun getElectiveSelectionId(studentId: Int, electiveId: Int): EntityID<Int>? {
            return StudentElectives
                .selectAll()
                .where { (StudentElectives.student eq studentId) and (StudentElectives.elective eq electiveId) }
                .singleOrNull()
                ?.get(StudentElectives.subject)
        }

        /**
         * Removes a selection from the specified elective.
         */
        fun removeElectiveSelection(studentId: Int, electiveId: Int) {
            val count = StudentElectives.deleteWhere {
                (StudentElectives.student eq studentId) and (StudentElectives.elective eq electiveId)
            }

            if (count == 0) throw EntityNotFoundException(ExceptionEntity.ELECTIVE_SELECTION)
        }
    }
}

class Teacher(id: EntityID<Int>) : Entity<Int>(id) {
    var user by User referencedOn Teachers.user
    val subjects by Subject via TeacherSubjects

    companion object : EntityClass<Int, Teacher>(Teachers) {
        fun assertExists(teacherId: Int) {
            if (!exists(teacherId)) throw EntityNotFoundException(ExceptionEntity.TEACHER)
        }

        fun getSubjects(teacherId: Int): List<Subject> {
            return (TeacherSubjects innerJoin Subjects)
                .selectAll().where { TeacherSubjects.teacher eq teacherId }
                .map { Subject.wrapRow(it) }
        }

        fun teachesSubject(teacherId: Int, subjectId: Int): Boolean {
            return TeacherSubjects
                .selectAll()
                .where { (TeacherSubjects.teacher eq teacherId) and (TeacherSubjects.subject eq subjectId) }
                .empty().not()
        }
    }
}

class Team(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, Team>(Teams) {
        fun assertExists(teamId: Int) {
            if (!exists(teamId)) throw EntityNotFoundException(ExceptionEntity.TEAM)
        }
    }

    val name by Teams.name
}

open class ElectiveCompanion : EntityClass<Int, Elective>(Electives) {
    fun assertExists(id: Int) {
        if (!exists(id)) throw EntityNotFoundException(ExceptionEntity.ELECTIVE)
    }

    fun getAllActiveIds(at: LocalDateTime = LocalDateTime.now()): List<Int> {
        return Electives
            .select(Electives.id)
            .where {
                ((Electives.startDate lessEq at) or (Electives.startDate.isNull())) and
                        ((Electives.endDate greaterEq at) or (Electives.endDate.isNull()))
            }
            .map { it[Electives.id].value }
    }

    /**
     * Gets the team ID for the specified elective.
     */
    fun getTeamId(electiveId: Int): EntityID<Int>? {
        return Electives
            .select(Electives.team)
            .where { Electives.id eq electiveId }
            .singleOrNull()
            ?.get(Electives.team)
    }

    fun getSubjectIds(electiveId: Int): List<EntityID<Int>> {
        return ElectiveSubjects
            .selectAll().where { ElectiveSubjects.elective eq electiveId }
            .map { it[ElectiveSubjects.subject] }
    }

    /**
     * Gets the subjects for the specified elective.
     */
    fun getSubjects(electiveId: Int): List<Subject> {
        return (ElectiveSubjects innerJoin Subjects)
            .selectAll().where { ElectiveSubjects.elective eq electiveId }
            .map { Subject.wrapRow(it) }
    }

    /**
     * Gets all subject IDs and their enrolled counts for the specified elective.
     *
     * @return Map<SubjectId, EnrolledCount>
     */
    fun getSubjectsEnrolledCounts(electiveId: Int): Map<Int, Int> {
        val subjectIds = getSubjectIds(electiveId)
        if (subjectIds.isEmpty()) return emptyMap()

        return StudentElectives
            .select(StudentElectives.subject, Count(StudentElectives.student))
            .where { (StudentElectives.elective eq electiveId) and (StudentElectives.subject inList subjectIds) }
            .groupBy(StudentElectives.subject)
            .associate { it[StudentElectives.subject].value to it[Count(StudentElectives.student)].toInt() }
    }

    fun getEnrollmentDateRange(electiveId: Int): Pair<LocalDateTime?, LocalDateTime?> {
        val row = Electives
            .selectAll()
            .where { Electives.id eq electiveId }
            .singleOrNull()
            ?: throw EntityNotFoundException(ExceptionEntity.ELECTIVE)

        return row[Electives.startDate] to row[Electives.endDate]
    }
}

class Elective(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : ElectiveCompanion()

    val name by Electives.name
    val teamId by Electives.team

    val subjects by Subject via ElectiveSubjects

    val startDate by Electives.startDate
    val endDate by Electives.endDate
}

open class SubjectCompanion : EntityClass<Int, Subject>(Subjects) {
    fun assertExists(id: Int) {
        if (!exists(id)) throw EntityNotFoundException(ExceptionEntity.SUBJECT)
    }

    /**
     * Gets the team ID for the specified subject.
     */
    fun getTeamId(subjectId: Int): EntityID<Int>? {
        return Subjects
            .select(Subjects.team)
            .where { Subjects.id eq subjectId }
            .singleOrNull()
            ?.get(Subjects.team)
    }

    /**
     * Gets teachers for the specified subject.
     */
    fun getTeachers(subjectId: Int): List<Teacher> {
        return (TeacherSubjects innerJoin Teachers innerJoin Users)
            .selectAll()
            .where { TeacherSubjects.subject eq subjectId }
            .map { Teacher.wrapRow(it) }
    }

    /**
     * Gets students for the specified subject of the specified elective.
     *
     * @throws Subject.NotPartOfElectiveException if the subject is not part of the specified elective.
     */
    fun getStudents(subjectId: Int, electiveId: Int): List<Student> {
        if (!isPartOfElective(subjectId, electiveId)) throw Subject.NotPartOfElectiveException(subjectId, electiveId)

        val studentIds = StudentElectives
            .select(StudentElectives.student)
            .where { (StudentElectives.subject eq subjectId) and (StudentElectives.elective eq electiveId) }
            .map { it[StudentElectives.student].value }

        if (studentIds.isEmpty()) return emptyList()

        return (Students innerJoin Users)
            .selectAll()
            .where { Students.id inList studentIds }
            .map { Student.wrapRow(it).load(Student::teams) }
    }

    /**
     * Checks if the subject is part of the specified elective.
     *
     * @throws Subject.NotPartOfElectiveException if the subject is not part of the specified elective.
     */
    fun isPartOfElective(subjectId: Int, electiveId: Int): Boolean {
        return (ElectiveSubjects innerJoin Subjects)
            .selectAll()
            .where { (ElectiveSubjects.elective eq electiveId) and (ElectiveSubjects.subject eq subjectId) }
            .empty().not()
    }

    /**
     * Gets the enrolled count for the specified subject of the specified elective.
     * @throws Subject.NotPartOfElectiveException if the subject is not part of the specified elective.
     */
    fun getEnrolledCount(subjectId: Int, electiveId: Int): Int {
        if (!isPartOfElective(subjectId, electiveId)) throw Subject.NotPartOfElectiveException(subjectId, electiveId)
        return StudentElectives
            .selectAll()
            .where { (StudentElectives.subject eq subjectId) and (StudentElectives.elective eq electiveId) }
            .count()
            .toInt()
    }
}

class Subject(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : SubjectCompanion()

    class NotPartOfElectiveException(subjectId: Int, electiveId: Int) :
        Exception("Subject $subjectId is not part of elective $electiveId")

    val name by Subjects.name

    fun getEnrolledCount(electiveId: Int) = getEnrolledCount(this@Subject.id.value, electiveId)

    val teachers by Teacher via TeacherSubjects

    val description by lazy {
        Subjects.select(Subjects.description)
            .where { Subjects.id eq id }
            .single()[Subjects.description]
    }

    val code by Subjects.code
    val tag by Subjects.tag
    val teamId by Subjects.team

    val location by Subjects.location
    val capacity by Subjects.capacity

    val imageUrl by Subjects.imageUrl
    val thumbnailUrl by Subjects.thumbnailUrl
}

fun <T : Any, U : Entity<T>> EntityClass<T, U>.exists(id: T): Boolean =
    this.table.selectAll().where { this.table.id eq id }.empty().not()