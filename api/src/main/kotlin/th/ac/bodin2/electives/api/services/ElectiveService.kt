package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.Teacher

interface ElectiveService {
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