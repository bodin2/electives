package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.Teacher
import java.time.LocalDateTime

interface ElectiveService {
    @CreatesTransaction
    fun create(
        id: Int,
        name: String,
        team: Int? = null,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null
    ): Elective

    /**
     * Deletes an elective by its ID.
     *
     * @throws th.ac.bodin2.electives.NotFoundException if the elective does not exist.
     */
    @CreatesTransaction
    fun delete(id: Int)

    /**
     * Updates an elective's information.
     */
    @CreatesTransaction
    fun update(id: Int, update: ElectiveUpdate)

    data class ElectiveUpdate(
        val name: String? = null,
        val team: Int? = null,
        val startDate: LocalDateTime? = null,
        val endDate: LocalDateTime? = null,
        val setTeam: Boolean = false,
        val setStartDate: Boolean = false,
        val setEndDate: Boolean = false,
    )

    fun getAll(): List<Elective>

    fun getById(electiveId: Int): Elective?

    fun getSubjects(electiveId: Int): QueryResult<out List<Subject>>

    fun getSubject(electiveId: Int, subjectId: Int): QueryResult<out Subject>

    fun getSubjectMembers(
        electiveId: Int,
        subjectId: Int,
        withStudents: Boolean,
    ): QueryResult<out Pair<List<Teacher>, List<Student>>>

    sealed class QueryResult<T> {
        data class Success<T>(val value: T) : QueryResult<T>()

        data object ElectiveNotFound : QueryResult<Nothing>()
        data object SubjectNotFound : QueryResult<Nothing>()
        data class SubjectNotPartOfElective(val subjectId: Int, val electiveId: Int) : QueryResult<Nothing>()
    }
}