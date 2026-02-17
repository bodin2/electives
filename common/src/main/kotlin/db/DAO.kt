package th.ac.bodin2.electives.db

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.db.models.*
import java.time.LocalDateTime

class User(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, User>(Users)

    val firstName by Users.firstName
    val middleName by Users.middleName
    val lastName by Users.lastName

    val avatarUrl by Users.avatarUrl
}

class Student(val reference: Reference, val user: User, val teams: List<Team>) {
    class Reference internal constructor(val id: Int) {
        companion object {
            fun from(row: ResultRow): Reference {
                val entityId = row.getOrNull(Students.id) ?: throw IllegalArgumentException("ResultRow does not contain student ID")
                return Reference(entityId.value)
            }
        }
    }

    val id = reference.id

    companion object {
        fun exists(id: Int): Boolean = Students.selectAll().where { Students.id eq id }.empty().not()

        fun require(id: Int): Reference {
            if (!exists(id)) throw NotFoundException(ExceptionEntity.STUDENT)
            return Reference(id)
        }

        fun findById(id: Int): Student? {
            if (!exists(id)) return null

            val user = User.findById(id) ?: return null
            val ref = Reference(id)
            return Student(ref, user, getTeams(ref))
        }

        fun hasTeam(student: Reference, teamId: EntityID<Int>) = hasTeam(student, teamId.value)
        fun hasTeam(student: Reference, teamId: Int): Boolean {
            return StudentTeams
                .selectAll().where { (StudentTeams.student eq student.id) and (StudentTeams.team eq teamId) }
                .empty().not()
        }

        fun getAllElectiveSelections(student: Reference): List<Pair<Int, Subject>> {
            return StudentElectives
                .selectAll().where { StudentElectives.student eq student.id }
                .map {
                    val electiveId = it[StudentElectives.elective]
                    val subjectId = it[StudentElectives.subject]
                    electiveId.value to Subject.findById(subjectId)!!
                }
        }

        fun getElectiveSelectionId(student: Reference, elective: Elective.Reference): EntityID<Int>? {
            return StudentElectives
                .selectAll()
                .where { (StudentElectives.student eq student.id) and (StudentElectives.elective eq elective.id) }
                .singleOrNull()
                ?.get(StudentElectives.subject)
        }

        /**
         * Removes a selection from the specified elective.
         */
        fun removeElectiveSelection(student: Reference, elective: Elective.Reference) {
            val studentId = student.id
            val electiveId = elective.id

            val count = StudentElectives.deleteWhere {
                (StudentElectives.student eq studentId) and (StudentElectives.elective eq electiveId)
            }

            if (count == 0) throw NotFoundException(ExceptionEntity.ELECTIVE_SELECTION)
        }

        fun getTeams(student: Reference): List<Team> {
            return (StudentTeams innerJoin Teams)
                .selectAll().where { StudentTeams.student eq student.id }
                .map { Team.wrapRow(it) }
        }

        fun new(id: Int, user: User): Student {
            Students.insert {
                it[Students.id] = id
            }

            return Student(Reference(id), user, emptyList())
        }
    }
}

class Teacher(val reference: Reference, val user: User) {
    class Reference internal constructor(val id: Int)

    companion object {
        fun from(user: ResultRow): Teacher {
            val id = user.getOrNull(Teachers.id)?.value ?: throw IllegalArgumentException("ResultRow does not contain teacher ID")
            return Teacher(Reference(id), User.wrapRow(user))
        }

        fun findById(id: Int): Teacher? {
            if (!exists(id)) return null

            val user = User.findById(id) ?: return null
            return Teacher(Reference(id), user)
        }

        fun exists(id: Int): Boolean =
            Teachers.selectAll().where { Teachers.id eq id }.empty().not()

        fun require(id: Int): Reference {
            if (!exists(id)) throw NotFoundException(ExceptionEntity.TEACHER)
            return Reference(id)
        }

        fun getSubjects(teacher: Reference): List<Subject> {
            return (TeacherSubjects innerJoin Subjects)
                .selectAll().where { TeacherSubjects.teacher eq teacher.id }
                .map { Subject.wrapRow(it) }
        }

        fun teachesSubject(teacher: Reference, subjectId: Int): Boolean {
            return TeacherSubjects
                .selectAll()
                .where { (TeacherSubjects.teacher eq teacher.id) and (TeacherSubjects.subject eq subjectId) }
                .empty().not()
        }

        fun new(id: Int, user: User): Teacher {
            Teachers.insert {
                it[Teachers.id] = id
            }

            return Teacher(Reference(id), user)
        }
    }

    val id = reference.id

    val subjects
        get() = getSubjects(reference)
}

class Team(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, Team>(Teams)

    val name by Teams.name
}

open class ElectiveCompanion : EntityClass<Int, Elective>(Electives) {
    fun exists(id: Int): Boolean =
        Electives.selectAll().where { Electives.id eq id }.empty().not()

    fun require(id: Int): Elective.Reference {
        if (!exists(id)) throw NotFoundException(ExceptionEntity.ELECTIVE)
        return Elective.Reference(id)
    }

    fun allActiveReferences(at: LocalDateTime = LocalDateTime.now()): List<Elective.Reference> {
        return Electives
            .selectAll()
            .where {
                ((Electives.startDate lessEq at) and (Electives.endDate greaterEq at)) or
                        (Electives.startDate.isNull() and Electives.endDate.isNull())
            }
            .map { Elective.Reference(it[Electives.id].value) }
    }

    /**
     * Gets the team ID for the specified elective.
     */
    fun getTeamId(elective: Elective.Reference): EntityID<Int>? {
        return Electives
            .select(Electives.team)
            .where { Electives.id eq elective.id }
            .singleOrNull()
            ?.get(Electives.team)
    }

    fun getSubjectIds(elective: Elective.Reference): List<EntityID<Int>> {
        return ElectiveSubjects
            .selectAll().where { ElectiveSubjects.elective eq elective.id }
            .map { it[ElectiveSubjects.subject] }
    }

    /**
     * Gets the subjects for the specified elective.
     */
    fun getSubjects(elective: Elective.Reference): List<Subject> {
        return (ElectiveSubjects innerJoin Subjects)
            .selectAll().where { ElectiveSubjects.elective eq elective.id }
            .map { Subject.wrapRow(it) }
    }

    /**
     * Gets all subject IDs and their enrolled counts for the specified elective.
     *
     * @return Map<SubjectId, EnrolledCount>
     */
    fun getSubjectsEnrolledCounts(elective: Elective.Reference): Map<Int, Int> =
        getSubjectIds(elective).associateBy({ it.value }, { subjectId ->
            StudentElectives
                .selectAll().where { StudentElectives.subject eq subjectId }
                .count()
                .toInt()
        })

    fun getEnrollmentDateRange(elective: Elective.Reference): Pair<LocalDateTime?, LocalDateTime?> {
        val row = Electives
            .selectAll()
            .where { Electives.id eq elective.id }
            .singleOrNull()
            ?: throw NotFoundException(ExceptionEntity.ELECTIVE)

        return row[Electives.startDate] to row[Electives.endDate]
    }
}

class Elective(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : ElectiveCompanion()

    /**
     * A lightweight reference to an Elective entity that exists.
     */
    class Reference internal constructor(val id: Int)

    val name by Electives.name
    val teamId by Electives.team

    val subjects by Subject via ElectiveSubjects

    val startDate by Electives.startDate
    val endDate by Electives.endDate
}

open class SubjectCompanion : EntityClass<Int, Subject>(Subjects) {
    fun exists(id: Int): Boolean =
        Subjects.selectAll().where { Subjects.id eq id }.empty().not()

    fun require(id: Int): Subject.Reference {
        if (!exists(id)) throw NotFoundException(ExceptionEntity.SUBJECT)
        return Subject.Reference(id)
    }

    /**
     * Gets the team ID for the specified subject.
     */
    fun getTeamId(subject: Subject.Reference): EntityID<Int>? {
        return Subjects
            .select(Subjects.team)
            .where { Subjects.id eq subject.id }
            .singleOrNull()
            ?.get(Subjects.team)
    }

    /**
     * Gets teachers for the specified subject.
     */
    fun getTeachers(subject: Subject.Reference): List<Teacher> {
        return (TeacherSubjects innerJoin Teachers)
            .selectAll().where { (TeacherSubjects.subject eq subject.id) }
            .map { Teacher.findById(it[Teachers.id].value)!! }
    }

    /**
     * Gets students for the specified subject of the specified elective.
     *
     * @throws Subject.NotPartOfElectiveException if the subject is not part of the specified elective.
     */
    fun getStudents(subject: Subject.Reference, elective: Elective.Reference): List<Student> {
        if (!isPartOfElective(subject, elective)) throw Subject.NotPartOfElectiveException(subject.id, elective.id)

        return (StudentElectives innerJoin Students)
            .selectAll().where {
                (StudentElectives.subject eq subject.id) and
                        (StudentElectives.elective eq elective.id)
            }
            .map { Student.findById(it[Students.id].value)!! }
    }

    /**
     * Checks if the subject is part of the specified elective.
     *
     * @throws Subject.NotPartOfElectiveException if the subject is not part of the specified elective.
     */
    fun isPartOfElective(subject: Subject.Reference, elective: Elective.Reference): Boolean {
        return (ElectiveSubjects innerJoin Subjects)
            .selectAll()
            .where { (ElectiveSubjects.elective eq elective.id) and (ElectiveSubjects.subject eq subject.id) }
            .empty().not()
    }

    /**
     * Gets the enrolled count for the specified subject of the specified elective.
     * @throws Subject.NotPartOfElectiveException if the subject is not part of the specified elective.
     */
    fun getEnrolledCount(subject: Subject.Reference, elective: Elective.Reference): Int {
        if (!isPartOfElective(subject, elective)) throw Subject.NotPartOfElectiveException(subject.id, elective.id)
        return StudentElectives
            .selectAll()
            .where { (StudentElectives.subject eq subject.id) and (StudentElectives.elective eq elective.id) }
            .count()
            .toInt()
    }

    fun getEnrolledCount(subjectId: EntityID<Int>, elective: Elective.Reference) =
        getEnrolledCount(Subject.Reference(subjectId.value), elective)
}

class Subject(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : SubjectCompanion()

    /**
     * A lightweight reference to a Subject entity that exists.
     */
    class Reference internal constructor(val id: Int)

    class NotPartOfElectiveException(subjectId: Int, electiveId: Int) :
        Exception("Subject $subjectId is not part of elective $electiveId")

    val name by Subjects.name

    fun getEnrolledCount(elective: Elective.Reference) = getEnrolledCount(this@Subject.id, elective)

    val teachers
        get() = getTeachers(Reference(id.value))

    val description
        get() = Subjects.select(Subjects.description)
            .where { Subjects.id eq id }
            .single()[Subjects.description]

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