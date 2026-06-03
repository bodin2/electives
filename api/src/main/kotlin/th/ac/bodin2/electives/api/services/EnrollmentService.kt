package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.*
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.EnrollmentService.QueryResult
import th.ac.bodin2.electives.api.utils.dbQuery
import th.ac.bodin2.electives.db.*
import th.ac.bodin2.electives.db.models.*
import java.time.LocalDateTime

class EnrollmentService {
    companion object {
        const val PAGE_SIZE = 50
    }

    /**
     * Creates a new enrollment with the given information.
     *
     * @throws EntityNotFoundException if the specified group does not exist.
     * @throws ConflictException if an enrollment with the same ID already exists.
     */
    @Transactional
    suspend fun create(
        id: Int,
        name: String,
        group: Int? = null,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null
    ) = dbQuery {
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


    /**
     * Deletes an enrollment by its ID.
     *
     * @throws EntityNotFoundException if the enrollment does not exist.
     */
    @Transactional
    suspend fun delete(id: Int) {
        dbQuery {
            val rows = Enrollments.deleteWhere { Enrollments.id eq id }
            if (rows == 0) {
                throw EntityNotFoundException(ExceptionEntity.ENROLLMENT)
            }
        }
    }

    /**
     * Updates an enrollment's information.
     *
     * @throws EntityNotFoundException if the enrollment does not exist.
     * @throws NothingToUpdateException if there's nothing to update.
     */
    @Transactional
    suspend fun update(id: Int, update: EnrollmentUpdate) = dbQuery {
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

    /**
     * Sets the subjects that are part of the enrollment.
     */
    @Transactional
    suspend fun setSubjects(enrollmentId: Int, subjectIds: List<Int>) {
        dbQuery {
            Enrollment.assertExists(enrollmentId)

            val subjectIds = subjectIds.distinct()
            subjectIds.forEach { Subject.assertExists(it) }

            EnrollmentSubjects.deleteWhere { EnrollmentSubjects.enrollment eq enrollmentId }
            EnrollmentSubjects.batchInsert(subjectIds) { subjectId ->
                this[EnrollmentSubjects.enrollment] = enrollmentId
                this[EnrollmentSubjects.subject] = subjectId
            }

            // Unenroll students and remove teachers from the class
            StudentClasses.deleteWhere { (StudentClasses.enrollment eq enrollmentId) and (StudentClasses.subject notInList subjectIds) }
            TeacherSubjects.deleteWhere { (TeacherSubjects.enrollment eq enrollmentId) and (TeacherSubjects.subject notInList subjectIds) }
        }
    }

    data class EnrollmentUpdate(
        val name: String? = null,
        val group: Int? = null,
        val startDate: LocalDateTime? = null,
        val endDate: LocalDateTime? = null,
        val setGroup: Boolean = false,
        val setStartDate: Boolean = false,
        val setEndDate: Boolean = false,
    )

    fun getAll() = Enrollment.all().toList()

    fun getById(enrollmentId: Int) = Enrollment.findById(enrollmentId)

    fun getSubjects(enrollmentId: Int): QueryResult<out List<Subject>> {
        if (!Enrollment.exists(enrollmentId)) return QueryResult.EnrollmentNotFound
        return QueryResult.Success(Enrollment.getSubjects(enrollmentId))
    }

    fun getSubject(enrollmentId: Int, subjectId: Int): QueryResult<out Subject> {
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

    fun getSubjectMembers(
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

    fun getEnrolledCount(enrollmentId: Int): Int =
        StudentClasses.selectAll()
            .where { StudentClasses.enrollment eq enrollmentId }
            .count().toInt()

    fun getUnenrolledMembers(
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

    sealed class QueryResult<T> {
        data class Success<T>(val value: T) : QueryResult<T>()

        data object EnrollmentNotFound : QueryResult<Nothing>()
        data object SubjectNotFound : QueryResult<Nothing>()
        data class SubjectNotPartOfEnrollment(val subjectId: Int, val enrollmentId: Int) : QueryResult<Nothing>()
    }
}
