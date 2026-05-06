package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.Teacher
import java.time.LocalDateTime

interface ElectiveService {
    /**
     * Creates a new elective with the given information.
     *
     * @throws EntityNotFoundException if the specified team does not exist.
     * @throws ConflictException if an elective with the same ID already exists.
     */
    @Transactional
    fun create(
        id: UInt,
        name: String,
        team: UInt? = null,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null
    ): Elective

    /**
     * Deletes an elective by its ID.
     *
     * @throws EntityNotFoundException if the elective does not exist.
     */
    @Transactional
    fun delete(id: UInt)

    /**
     * Updates an elective's information.
     *
     * @throws EntityNotFoundException if the elective does not exist.
     * @throws NothingToUpdateException if there's nothing to update.
     */
    @Transactional
    fun update(id: UInt, update: ElectiveUpdate): Elective

    /**
     * Sets the subjects that are part of the elective.
     */
    @Transactional
    fun setSubjects(electiveId: UInt, subjectIds: List<UInt>)

    data class ElectiveUpdate(
        val name: String? = null,
        val team: UInt? = null,
        val startDate: LocalDateTime? = null,
        val endDate: LocalDateTime? = null,
        val setTeam: Boolean = false,
        val setStartDate: Boolean = false,
        val setEndDate: Boolean = false,
    )

    fun getAll(): List<Elective>

    fun getById(electiveId: UInt): Elective?

    fun getSubjects(electiveId: UInt): QueryResult<out List<Subject>>

    fun getSubject(electiveId: UInt, subjectId: UInt): QueryResult<out Subject>

    fun getSubjectMembers(
        electiveId: UInt,
        subjectId: UInt,
        withStudents: Boolean,
    ): QueryResult<out Pair<List<Teacher>, List<Student>>>

    fun getEnrolledCount(electiveId: UInt): Int

    fun getUnenrolledMembers(
        electiveId: UInt,
        teamId: UInt,
        page: Int,
    ): QueryResult<out Pair<List<Student>, Long>>

    sealed class QueryResult<T> {
        data class Success<T>(val value: T) : QueryResult<T>()

        data object ElectiveNotFound : QueryResult<Nothing>()
        data object SubjectNotFound : QueryResult<Nothing>()
        data class SubjectNotPartOfElective(val subjectId: UInt, val electiveId: UInt) : QueryResult<Nothing>()
    }
}