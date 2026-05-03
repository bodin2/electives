package th.ac.bodin2.electives.api.services.mock

import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.MockUtils
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.ElectiveService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ELECTIVE_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ELECTIVE_WITHOUT_SUBJECTS_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.TEACHER_ID
import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.Teacher
import java.time.LocalDateTime

class TestElectiveService : ElectiveService {

    @Transactional
    override fun create(
        id: Int,
        name: String,
        team: Int?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ): Elective = error("Not testable")

    @Transactional
    override fun delete(id: Int) = error("Not testable")

    @Transactional
    override fun update(id: Int, update: ElectiveService.ElectiveUpdate): Elective {
        if (id !in ELECTIVE_IDS) throw EntityNotFoundException(ExceptionEntity.ELECTIVE)
        return MockUtils.mockElective(id)
    }

    @Transactional
    override fun setSubjects(electiveId: Int, subjectIds: List<Int>) = error("Not testable")

    companion object {
        val ELECTIVE_IDS = listOf(
            ELECTIVE_ID,
            ELECTIVE_WITHOUT_SUBJECTS_ID,
        )
    }

    override fun getAll() = ELECTIVE_IDS.map { id -> MockUtils.mockElective(id) }

    override fun getById(electiveId: Int) =
        if (electiveId in ELECTIVE_IDS) MockUtils.mockElective(electiveId)
        else null

    override fun getSubjects(electiveId: Int): ElectiveService.QueryResult<out List<Subject>> {
        val elective = getById(electiveId)
            ?: return ElectiveService.QueryResult.ElectiveNotFound

        return ElectiveService.QueryResult.Success(elective.subjects.toList())
    }

    override fun getSubject(
        electiveId: Int,
        subjectId: Int
    ): ElectiveService.QueryResult<out Subject> {
        val elective = getById(electiveId)
            ?: return ElectiveService.QueryResult.ElectiveNotFound

        if (subjectId != SUBJECT_ID) {
            return ElectiveService.QueryResult.SubjectNotFound
        }

        val subject = elective.subjects.find { it.id.value == subjectId }
            ?: return ElectiveService.QueryResult.SubjectNotPartOfElective(subjectId, electiveId)

        return ElectiveService.QueryResult.Success(subject)
    }

    override fun getSubjectMembers(
        electiveId: Int,
        subjectId: Int,
        withStudents: Boolean,
    ): ElectiveService.QueryResult<out Pair<List<Teacher>, List<Student>>> {
        return when (val result = getSubject(electiveId, subjectId)) {
            is ElectiveService.QueryResult.ElectiveNotFound,
            is ElectiveService.QueryResult.SubjectNotFound,
            is ElectiveService.QueryResult.SubjectNotPartOfElective ->
                result

            else ->
                ElectiveService.QueryResult.Success(
                    listOf(MockUtils.mockTeacher(TEACHER_ID)) to buildList {
                        if (withStudents) add(MockUtils.mockStudent(STUDENT_ID))
                    }
                )
        }
    }

    override fun getEnrolledCount(electiveId: Int): Int = 0

    override fun getUnenrolledMembers(
        electiveId: Int,
        teamId: Int,
        page: Int
    ): ElectiveService.QueryResult<out Pair<List<Student>, Long>> {
        val elective = getById(electiveId)
            ?: return ElectiveService.QueryResult.ElectiveNotFound

        return ElectiveService.QueryResult.Success(
            listOf(MockUtils.mockStudent(STUDENT_ID)) to 1L
        )
    }
}