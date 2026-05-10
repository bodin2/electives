package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.EnrollmentService.QueryResult
import th.ac.bodin2.electives.db.*
import th.ac.bodin2.electives.db.models.*
import java.time.LocalDateTime

class EnrollmentServiceImpl : EnrollmentService {
    companion object {
        const val PAGE_SIZE = 50
    }

    @Transactional
    override fun create(
        id: Int,
        name: String,
        group: Int?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ) = transaction {
        val stmt = Enrollments.insertIgnore {
            it[this.id] = id
            it[this.name] = name
            it[this.startDate] = startDate
            it[this.endDate] = endDate

            if (group != null) {
                if (!Group.exists(group)) throw EntityNotFoundException(ExceptionEntity.GROUP)
                it[this.group] = group
            }
        }

        if (stmt.insertedCount == 0) throw ConflictException(ExceptionEntity.ENROLLMENT)
        Enrollment.wrapRow(stmt.resultedValues!!.first())
    }


    @Transactional
    override fun delete(id: Int) {
        transaction {
            val rows = Enrollments.deleteWhere { Enrollments.id eq id }
            if (rows == 0) {
                throw EntityNotFoundException(ExceptionEntity.ENROLLMENT)
            }
        }
    }

    @Transactional
    override fun update(id: Int, update: EnrollmentService.EnrollmentUpdate) = transaction {
        Enrollment.assertExists(id)

        val rows = Enrollments.updateReturning(where = { Enrollments.id eq id }) {
            update.name?.let { name -> it[this.name] = name }
            if (update.setStartDate) it[this.startDate] = update.startDate
            if (update.setEndDate) it[this.endDate] = update.endDate
            if (update.setGroup) {
                if (update.group != null && !Group.exists(update.group)) throw EntityNotFoundException(ExceptionEntity.GROUP)
                it[this.group] = update.group
            }

            if (it.firstDataSet.isEmpty()) throw NothingToUpdateException()
        }
        Enrollment.wrapRow(rows.first())
    }

    @Transactional
    override fun setSubjects(enrollmentId: Int, subjectIds: List<Int>) {
        transaction {
            Enrollment.assertExists(enrollmentId)

            val subjectIds = subjectIds.distinct()
            subjectIds.forEach { Subject.assertExists(it) }

            EnrollmentSubjects.deleteWhere { EnrollmentSubjects.enrollment eq enrollmentId }
            EnrollmentSubjects.batchInsert(subjectIds) { subjectId ->
                this[EnrollmentSubjects.enrollment] = enrollmentId
                this[EnrollmentSubjects.subject] = subjectId
            }
        }
    }

    override fun getAll() = Enrollment.all().toList()

    override fun getById(enrollmentId: Int) = Enrollment.findById(enrollmentId)

    override fun getSubjects(enrollmentId: Int): QueryResult<out List<Subject>> {
        if (!Enrollment.exists(enrollmentId)) return QueryResult.EnrollmentNotFound
        return QueryResult.Success(Enrollment.getSubjects(enrollmentId))
    }

    override fun getSubject(enrollmentId: Int, subjectId: Int): QueryResult<out Subject> {
        try {
            Enrollment.assertExists(enrollmentId)
            Subject.assertExists(subjectId)

            if (!Subject.isPartOfEnrollment(subjectId, enrollmentId)) {
                return QueryResult.SubjectNotPartOfEnrollment(subjectId, enrollmentId)
            }

            return QueryResult.Success(Subject.findById(subjectId)!!)
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.SUBJECT -> QueryResult.SubjectNotFound
                ExceptionEntity.ENROLLMENT -> QueryResult.EnrollmentNotFound
                else -> throw e
            }
        }
    }

    override fun getSubjectMembers(
        enrollmentId: Int,
        subjectId: Int,
        withStudents: Boolean,
    ): QueryResult<out Pair<List<Teacher>, List<Student>>> {
        try {
            Enrollment.assertExists(enrollmentId)
            Subject.assertExists(subjectId)

            if (!Subject.isPartOfEnrollment(subjectId, enrollmentId)) {
                return QueryResult.SubjectNotPartOfEnrollment(subjectId, enrollmentId)
            }

            val teachers = Subject.getTeachers(subjectId, enrollmentId)
            val students = if (withStudents) Subject.getStudents(subjectId, enrollmentId) else emptyList()

            return QueryResult.Success(teachers to students)
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.SUBJECT -> QueryResult.SubjectNotFound
                ExceptionEntity.ENROLLMENT -> QueryResult.EnrollmentNotFound
                else -> throw e
            }
        }
    }

    override fun getEnrolledCount(enrollmentId: Int): Int =
        StudentClasses.selectAll()
            .where { StudentClasses.enrollment eq enrollmentId }
            .count().toInt()

    override fun getUnenrolledMembers(
        enrollmentId: Int,
        groupId: Int,
        page: Int
    ): QueryResult<out Pair<List<Student>, Long>> {
        require(page >= 1) { "Page must be at least 1" }
        if (!Enrollment.exists(enrollmentId)) return QueryResult.EnrollmentNotFound
        if (!Group.exists(groupId)) throw EntityNotFoundException(ExceptionEntity.GROUP)

        val enrolledStudents = StudentClasses
            .select(StudentClasses.student)
            .where { StudentClasses.enrollment eq enrollmentId }

        val query = (Students innerJoin StudentGroups)
            .select(Students.columns)
            .where { (StudentGroups.group eq groupId) and (Students.id notInSubQuery enrolledStudents) }
            .orderBy(Students.id)

        val total = query.count()

        val students = Student.wrapRows(query.limit(PAGE_SIZE).offset(((page - 1) * PAGE_SIZE).toLong()))
            .with(Student::user, Student::groups).toList()

        return QueryResult.Success(students to total)
    }
}
