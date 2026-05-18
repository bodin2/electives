package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.db.Enrollment
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.Teacher
import java.time.LocalDateTime

interface EnrollmentService {
    /**
     * Creates a new enrollment with the given information.
     *
     * @throws EntityNotFoundException if the specified group does not exist.
     * @throws ConflictException if an enrollment with the same ID already exists.
     */
    @Transactional
    fun create(
        id: Int,
        name: String,
        group: Int? = null,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null
    ): Enrollment

    /**
     * Deletes an enrollment by its ID.
     *
     * @throws EntityNotFoundException if the enrollment does not exist.
     */
    @Transactional
    fun delete(id: Int)

    /**
     * Updates an enrollment's information.
     *
     * @throws EntityNotFoundException if the enrollment does not exist.
     * @throws NothingToUpdateException if there's nothing to update.
     */
    @Transactional
    fun update(id: Int, update: EnrollmentUpdate): Enrollment

    /**
     * Sets the subjects that are part of the enrollment.
     */
    @Transactional
    fun setSubjects(enrollmentId: Int, subjectIds: List<Int>)

    data class EnrollmentUpdate(
        val name: String? = null,
        val group: Int? = null,
        val startDate: LocalDateTime? = null,
        val endDate: LocalDateTime? = null,
        val setGroup: Boolean = false,
        val setStartDate: Boolean = false,
        val setEndDate: Boolean = false,
    )

    fun getAll(): List<Enrollment>

    fun getById(enrollmentId: Int): Enrollment?

    fun getSubjects(enrollmentId: Int): QueryResult<out List<Subject>>

    fun getSubject(enrollmentId: Int, subjectId: Int): QueryResult<out Subject>

    fun getSubjectMembers(
        enrollmentId: Int,
        subjectId: Int,
        withStudents: Boolean,
    ): QueryResult<out Pair<List<Teacher>, List<Student>>>

    fun getEnrolledCount(enrollmentId: Int): Int

    fun getUnenrolledMembers(
        enrollmentId: Int,
        groupId: Int,
        page: Int,
    ): QueryResult<out Pair<List<Student>, Long>>

    sealed class QueryResult<T> {
        data class Success<T>(val value: T) : QueryResult<T>()

        data object EnrollmentNotFound : QueryResult<Nothing>()
        data object SubjectNotFound : QueryResult<Nothing>()
        data class SubjectNotPartOfEnrollment(val subjectId: Int, val enrollmentId: Int) : QueryResult<Nothing>()
    }
}
