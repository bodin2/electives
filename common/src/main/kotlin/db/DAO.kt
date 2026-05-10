package th.ac.bodin2.electives.db

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.db.models.*
import java.time.LocalDateTime

class User(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, User>(Users)

    var prefix by Users.prefix
    var firstName by Users.firstName
    var middleName by Users.middleName
    var lastName by Users.lastName

    var avatarUrl by Users.avatarUrl
}

class Student(id: EntityID<Int>) : Entity<Int>(id) {
    var user by User referencedOn Students.user
    var groups by Group via StudentGroups

    companion object : EntityClass<Int, Student>(Students) {
        fun assertExists(studentId: Int) {
            if (!exists(studentId)) throw EntityNotFoundException(ExceptionEntity.STUDENT)
        }

        fun hasGroup(studentId: Int, groupId: EntityID<Int>) = hasGroup(studentId, groupId.value)
        fun hasGroup(studentId: Int, groupId: Int): Boolean {
            return StudentGroups
                .selectAll().where { (StudentGroups.student eq studentId) and (StudentGroups.group eq groupId) }
                .empty().not()
        }

        fun getAllEnrollmentSelections(studentId: Int): List<Pair<Int, Subject>> {
            return (StudentClasses innerJoin Subjects)
                .selectAll().where { StudentClasses.student eq studentId }
                .map {
                    val enrollmentId = it[StudentClasses.enrollment]
                    enrollmentId.value to Subject.wrapRow(it)
                }
        }

        fun getEnrollmentSelectionId(studentId: Int, enrollmentId: Int): EntityID<Int>? {
            return StudentClasses
                .selectAll()
                .where { (StudentClasses.student eq studentId) and (StudentClasses.enrollment eq enrollmentId) }
                .singleOrNull()
                ?.get(StudentClasses.subject)
        }

        /**
         * Removes a selection from the specified enrollment.
         */
        fun removeEnrollmentSelection(studentId: Int, enrollmentId: Int) {
            val count = StudentClasses.deleteWhere {
                (StudentClasses.student eq studentId) and (StudentClasses.enrollment eq enrollmentId)
            }

            if (count == 0) throw EntityNotFoundException(ExceptionEntity.ENROLLMENT_SELECTION)
        }
    }
}

class Teacher(id: EntityID<Int>) : Entity<Int>(id) {
    var user by User referencedOn Teachers.user

    companion object : EntityClass<Int, Teacher>(Teachers) {
        fun assertExists(teacherId: Int) {
            if (!exists(teacherId)) throw EntityNotFoundException(ExceptionEntity.TEACHER)
        }

        fun getSubjects(teacherId: Int, enrollmentId: Int): List<Subject> {
            return (TeacherSubjects innerJoin Subjects)
                .selectAll()
                .where { (TeacherSubjects.teacher eq teacherId) and (TeacherSubjects.enrollment eq enrollmentId) }
                .map { Subject.wrapRow(it) }
        }

        fun teachesSubject(teacherId: Int, subjectId: Int, enrollmentId: Int): Boolean {
            return TeacherSubjects
                .selectAll()
                .where {
                    (TeacherSubjects.teacher eq teacherId) and
                            (TeacherSubjects.subject eq subjectId) and
                            (TeacherSubjects.enrollment eq enrollmentId)
                }
                .empty().not()
        }
    }
}

class Admin(id: EntityID<Int>) : Entity<Int>(id) {
    var user by User referencedOn Admins.user
    var publicKey by Admins.publicKey

    companion object : EntityClass<Int, Admin>(Admins) {
        fun assertExists(adminId: Int) {
            if (!exists(adminId)) throw EntityNotFoundException(ExceptionEntity.USER)
        }
    }
}

class Group(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, Group>(Groups) {
        fun assertExists(groupId: Int) {
            if (!exists(groupId)) throw EntityNotFoundException(ExceptionEntity.GROUP)
        }
    }

    val name by Groups.name
}

open class EnrollmentCompanion : EntityClass<Int, Enrollment>(Enrollments) {
    fun assertExists(id: Int) {
        if (!exists(id)) throw EntityNotFoundException(ExceptionEntity.ENROLLMENT)
    }

    fun getAllActiveIds(at: LocalDateTime = LocalDateTime.now()): List<Int> {
        return Enrollments
            .select(Enrollments.id)
            .where {
                ((Enrollments.startDate lessEq at) or (Enrollments.startDate.isNull())) and
                        ((Enrollments.endDate greaterEq at) or (Enrollments.endDate.isNull()))
            }
            .map { it[Enrollments.id].value }
    }

    /**
     * Gets the group ID for the specified enrollment.
     */
    fun getGroupId(enrollmentId: Int): EntityID<Int>? {
        return Enrollments
            .select(Enrollments.group)
            .where { Enrollments.id eq enrollmentId }
            .singleOrNull()
            ?.get(Enrollments.group)
    }

    fun getSubjectIds(enrollmentId: Int): List<EntityID<Int>> {
        return EnrollmentSubjects
            .selectAll().where { EnrollmentSubjects.enrollment eq enrollmentId }
            .map { it[EnrollmentSubjects.subject] }
    }

    /**
     * Gets the subjects for the specified enrollment.
     */
    fun getSubjects(enrollmentId: Int): List<Subject> {
        return (EnrollmentSubjects innerJoin Subjects)
            .selectAll().where { EnrollmentSubjects.enrollment eq enrollmentId }
            .map { Subject.wrapRow(it) }
    }

    /**
     * Gets all subject IDs and their enrolled counts for the specified enrollment.
     *
     * @return Map<SubjectId, EnrolledCount>
     */
    fun getSubjectsEnrolledCounts(enrollmentId: Int): Map<Int, Int> {
        val subjectIds = getSubjectIds(enrollmentId)
        if (subjectIds.isEmpty()) return emptyMap()

        val counts = StudentClasses
            .select(StudentClasses.subject, Count(StudentClasses.student))
            .where { (StudentClasses.enrollment eq enrollmentId) and (StudentClasses.subject inList subjectIds) }
            .groupBy(StudentClasses.subject)
            .associate { it[StudentClasses.subject].value to it[Count(StudentClasses.student)].toInt() }

        return subjectIds.associate { it.value to (counts[it.value] ?: 0) }
    }

    fun getEnrollmentDateRange(enrollmentId: Int): Pair<LocalDateTime?, LocalDateTime?> {
        val row = Enrollments
            .selectAll()
            .where { Enrollments.id eq enrollmentId }
            .singleOrNull()
            ?: throw EntityNotFoundException(ExceptionEntity.ENROLLMENT)

        return row[Enrollments.startDate] to row[Enrollments.endDate]
    }
}

class Enrollment(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EnrollmentCompanion()

    val name by Enrollments.name
    val groupId by Enrollments.group

    val subjects by Subject via EnrollmentSubjects

    val startDate by Enrollments.startDate
    val endDate by Enrollments.endDate
}

open class SubjectCompanion : EntityClass<Int, Subject>(Subjects) {
    fun assertExists(id: Int) {
        if (!exists(id)) throw EntityNotFoundException(ExceptionEntity.SUBJECT)
    }

    /**
     * Gets the group ID for the specified subject.
     */
    fun getGroupId(subjectId: Int): EntityID<Int>? {
        return Subjects
            .select(Subjects.group)
            .where { Subjects.id eq subjectId }
            .singleOrNull()
            ?.get(Subjects.group)
    }

    /**
     * Gets teachers for the specified subject of the specified enrollment.
     */
    fun getTeachers(subjectId: Int, enrollmentId: Int): List<Teacher> {
        val query = (TeacherSubjects innerJoin Teachers)
            .select(Teachers.columns)
            .where { (TeacherSubjects.subject eq subjectId) and (TeacherSubjects.enrollment eq enrollmentId) }

        return Teacher.wrapRows(query).with(Teacher::user).toList()
    }

    /**
     * Gets students for the specified subject of the specified enrollment.
     *
     * @throws Subject.NotPartOfEnrollmentException if the subject is not part of the specified enrollment.
     */
    fun getStudents(subjectId: Int, enrollmentId: Int): List<Student> {
        if (!isPartOfEnrollment(subjectId, enrollmentId)) throw Subject.NotPartOfEnrollmentException(
            subjectId,
            enrollmentId
        )

        val query = (StudentClasses innerJoin Students)
            .select(Students.columns)
            .where { (StudentClasses.subject eq subjectId) and (StudentClasses.enrollment eq enrollmentId) }

        return Student.wrapRows(query).with(Student::user, Student::groups).toList()
    }

    /**
     * Checks if the subject is part of the specified enrollment.
     *
     * @throws Subject.NotPartOfEnrollmentException if the subject is not part of the specified enrollment.
     */
    fun isPartOfEnrollment(subjectId: Int, enrollmentId: Int): Boolean {
        return (EnrollmentSubjects innerJoin Subjects)
            .selectAll()
            .where { (EnrollmentSubjects.enrollment eq enrollmentId) and (EnrollmentSubjects.subject eq subjectId) }
            .empty().not()
    }

    /**
     * Gets the enrolled count for the specified subject of the specified enrollment.
     * @throws Subject.NotPartOfEnrollmentException if the subject is not part of the specified enrollment.
     */
    fun getEnrolledCount(subjectId: Int, enrollmentId: Int): Int {
        if (!isPartOfEnrollment(subjectId, enrollmentId)) throw Subject.NotPartOfEnrollmentException(
            subjectId,
            enrollmentId
        )
        return StudentClasses
            .selectAll()
            .where { (StudentClasses.subject eq subjectId) and (StudentClasses.enrollment eq enrollmentId) }
            .count()
            .toInt()
    }
}

class Subject(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : SubjectCompanion()

    class NotPartOfEnrollmentException(subjectId: Int, enrollmentId: Int) :
        Exception("Subject $subjectId is not part of enrollment $enrollmentId")

    val name by Subjects.name

    fun getEnrolledCount(enrollmentId: Int) = getEnrolledCount(this@Subject.id.value, enrollmentId)

    fun getTeachers(enrollmentId: Int) = getTeachers(this@Subject.id.value, enrollmentId)

    val description by lazy {
        Subjects.select(Subjects.description)
            .where { Subjects.id eq id }
            .single()[Subjects.description]
    }

    val code by Subjects.code
    val tag by Subjects.tag
    val groupId by Subjects.group

    val location by Subjects.location
    val capacity by Subjects.capacity

    val imageUrl by Subjects.imageUrl
    val thumbnailUrl by Subjects.thumbnailUrl
}

fun <T : Any, U : Entity<T>> EntityClass<T, U>.exists(id: T): Boolean =
    this.table.selectAll().where { this.table.id eq id }.empty().not()
