package th.ac.bodin2.electives.db

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.db.models.*
import java.time.LocalDateTime

class User(id: EntityID<UInt>) : UIntEntity(id) {
    companion object : UIntEntityClass<User>(Users)

    var firstName by Users.firstName
    var middleName by Users.middleName
    var lastName by Users.lastName

    var avatarUrl by Users.avatarUrl
}

class Student(id: EntityID<UInt>) : UIntEntity(id) {
    var user by User referencedOn Students.user
    var teams by Team via StudentTeams

    companion object : UIntEntityClass<Student>(Students) {
        fun assertExists(studentId: UInt) {
            if (!exists(studentId)) throw EntityNotFoundException(ExceptionEntity.STUDENT)
        }

        fun hasTeam(studentId: UInt, teamId: EntityID<UInt>) = hasTeam(studentId, teamId.value)
        fun hasTeam(studentId: UInt, teamId: UInt): Boolean {
            return StudentTeams
                .selectAll().where { (StudentTeams.student eq studentId) and (StudentTeams.team eq teamId) }
                .empty().not()
        }

        fun getAllElectiveSelections(studentId: UInt): List<Pair<UInt, Subject>> {
            return (StudentElectives innerJoin Subjects)
                .selectAll().where { StudentElectives.student eq studentId }
                .map {
                    val electiveId = it[StudentElectives.elective]
                    electiveId.value to Subject.wrapRow(it)
                }
        }

        fun getElectiveSelectionId(studentId: UInt, electiveId: UInt): EntityID<UInt>? {
            return StudentElectives
                .selectAll()
                .where { (StudentElectives.student eq studentId) and (StudentElectives.elective eq electiveId) }
                .singleOrNull()
                ?.get(StudentElectives.subject)
        }

        /**
         * Removes a selection from the specified elective.
         */
        fun removeElectiveSelection(studentId: UInt, electiveId: UInt) {
            val count = StudentElectives.deleteWhere {
                (StudentElectives.student eq studentId) and (StudentElectives.elective eq electiveId)
            }

            if (count == 0) throw EntityNotFoundException(ExceptionEntity.ELECTIVE_SELECTION)
        }
    }
}

class Teacher(id: EntityID<UInt>) : UIntEntity(id) {
    var user by User referencedOn Teachers.user

    companion object : UIntEntityClass<Teacher>(Teachers) {
        fun assertExists(teacherId: UInt) {
            if (!exists(teacherId)) throw EntityNotFoundException(ExceptionEntity.TEACHER)
        }

        fun getSubjects(teacherId: UInt, electiveId: UInt): List<Subject> {
            return (TeacherSubjects innerJoin Subjects)
                .selectAll()
                .where { (TeacherSubjects.teacher eq teacherId) and (TeacherSubjects.elective eq electiveId) }
                .map { Subject.wrapRow(it) }
        }

        fun teachesSubject(teacherId: UInt, subjectId: UInt, electiveId: UInt): Boolean {
            return TeacherSubjects
                .selectAll()
                .where {
                    (TeacherSubjects.teacher eq teacherId) and
                            (TeacherSubjects.subject eq subjectId) and
                            (TeacherSubjects.elective eq electiveId)
                }
                .empty().not()
        }
    }
}

class Admin(id: EntityID<UInt>) : UIntEntity(id) {
    var user by User referencedOn Admins.user
    var publicKey by Admins.publicKey

    companion object : UIntEntityClass<Admin>(Admins) {
        fun assertExists(adminId: UInt) {
            if (!exists(adminId)) throw EntityNotFoundException(ExceptionEntity.USER)
        }
    }
}

class Team(id: EntityID<UInt>) : UIntEntity(id) {
    companion object : UIntEntityClass<Team>(Teams) {
        fun assertExists(teamId: UInt) {
            if (!exists(teamId)) throw EntityNotFoundException(ExceptionEntity.TEAM)
        }
    }

    val name by Teams.name
}

open class ElectiveCompanion : UIntEntityClass<Elective>(Electives) {
    fun assertExists(id: UInt) {
        if (!exists(id)) throw EntityNotFoundException(ExceptionEntity.ELECTIVE)
    }

    fun getAllActiveIds(at: LocalDateTime = LocalDateTime.now()): List<UInt> {
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
    fun getTeamId(electiveId: UInt): EntityID<UInt>? {
        return Electives
            .select(Electives.team)
            .where { Electives.id eq electiveId }
            .singleOrNull()
            ?.get(Electives.team)
    }

    fun getSubjectIds(electiveId: UInt): List<EntityID<UInt>> {
        return ElectiveSubjects
            .selectAll().where { ElectiveSubjects.elective eq electiveId }
            .map { it[ElectiveSubjects.subject] }
    }

    /**
     * Gets the subjects for the specified elective.
     */
    fun getSubjects(electiveId: UInt): List<Subject> {
        return (ElectiveSubjects innerJoin Subjects)
            .selectAll().where { ElectiveSubjects.elective eq electiveId }
            .map { Subject.wrapRow(it) }
    }

    /**
     * Gets all subject IDs and their enrolled counts for the specified elective.
     *
     * @return Map<SubjectId, EnrolledCount>
     */
    fun getSubjectsEnrolledCounts(electiveId: UInt): Map<UInt, Int> {
        val subjectIds = getSubjectIds(electiveId)
        if (subjectIds.isEmpty()) return emptyMap()

        val counts = StudentElectives
            .select(StudentElectives.subject, Count(StudentElectives.student))
            .where { (StudentElectives.elective eq electiveId) and (StudentElectives.subject inList subjectIds) }
            .groupBy(StudentElectives.subject)
            .associate { it[StudentElectives.subject].value to it[Count(StudentElectives.student)].toInt() }

        return subjectIds.associate { it.value to (counts[it.value] ?: 0) }
    }

    fun getEnrollmentDateRange(electiveId: UInt): Pair<LocalDateTime?, LocalDateTime?> {
        val row = Electives
            .selectAll()
            .where { Electives.id eq electiveId }
            .singleOrNull()
            ?: throw EntityNotFoundException(ExceptionEntity.ELECTIVE)

        return row[Electives.startDate] to row[Electives.endDate]
    }
}

class Elective(id: EntityID<UInt>) : UIntEntity(id) {
    companion object : ElectiveCompanion()

    val name by Electives.name
    val teamId by Electives.team

    val subjects by Subject via ElectiveSubjects

    val startDate by Electives.startDate
    val endDate by Electives.endDate
}

open class SubjectCompanion : UIntEntityClass<Subject>(Subjects) {
    fun assertExists(id: UInt) {
        if (!exists(id)) throw EntityNotFoundException(ExceptionEntity.SUBJECT)
    }

    /**
     * Gets the team ID for the specified subject.
     */
    fun getTeamId(subjectId: UInt): EntityID<UInt>? {
        return Subjects
            .select(Subjects.team)
            .where { Subjects.id eq subjectId }
            .singleOrNull()
            ?.get(Subjects.team)
    }

    /**
     * Gets teachers for the specified subject of the specified elective.
     */
    fun getTeachers(subjectId: UInt, electiveId: UInt): List<Teacher> {
        val query = (TeacherSubjects innerJoin Teachers)
            .select(Teachers.columns)
            .where { (TeacherSubjects.subject eq subjectId) and (TeacherSubjects.elective eq electiveId) }

        return Teacher.wrapRows(query).with(Teacher::user).toList()
    }

    /**
     * Gets students for the specified subject of the specified elective.
     *
     * @throws Subject.NotPartOfElectiveException if the subject is not part of the specified elective.
     */
    fun getStudents(subjectId: UInt, electiveId: UInt): List<Student> {
        if (!isPartOfElective(subjectId, electiveId)) throw Subject.NotPartOfElectiveException(subjectId, electiveId)

        val query = (StudentElectives innerJoin Students)
            .select(Students.columns)
            .where { (StudentElectives.subject eq subjectId) and (StudentElectives.elective eq electiveId) }

        return Student.wrapRows(query).with(Student::user, Student::teams).toList()
    }

    /**
     * Checks if the subject is part of the specified elective.
     *
     * @throws Subject.NotPartOfElectiveException if the subject is not part of the specified elective.
     */
    fun isPartOfElective(subjectId: UInt, electiveId: UInt): Boolean {
        return (ElectiveSubjects innerJoin Subjects)
            .selectAll()
            .where { (ElectiveSubjects.elective eq electiveId) and (ElectiveSubjects.subject eq subjectId) }
            .empty().not()
    }

    /**
     * Gets the enrolled count for the specified subject of the specified elective.
     * @throws Subject.NotPartOfElectiveException if the subject is not part of the specified elective.
     */
    fun getEnrolledCount(subjectId: UInt, electiveId: UInt): Int {
        if (!isPartOfElective(subjectId, electiveId)) throw Subject.NotPartOfElectiveException(subjectId, electiveId)
        return StudentElectives
            .selectAll()
            .where { (StudentElectives.subject eq subjectId) and (StudentElectives.elective eq electiveId) }
            .count()
            .toInt()
    }
}

class Subject(id: EntityID<UInt>) : UIntEntity(id) {
    companion object : SubjectCompanion()

    class NotPartOfElectiveException(subjectId: UInt, electiveId: UInt) :
        Exception("Subject $subjectId is not part of elective $electiveId")

    val name by Subjects.name

    fun getEnrolledCount(electiveId: UInt) = getEnrolledCount(this@Subject.id.value, electiveId)

    fun getTeachers(electiveId: UInt) = getTeachers(this@Subject.id.value, electiveId)

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
