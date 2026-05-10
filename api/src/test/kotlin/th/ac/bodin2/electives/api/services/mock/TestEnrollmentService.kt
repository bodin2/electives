package th.ac.bodin2.electives.api.services.mock

import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.MockUtils
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.EnrollmentService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_WITHOUT_SUBJECTS_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.TEACHER_ID
import th.ac.bodin2.electives.db.Enrollment
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.Teacher
import java.time.LocalDateTime

class TestEnrollmentService : EnrollmentService {

    @Transactional
    override fun create(
        id: Int,
        name: String,
        group: Int?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ): Enrollment = error("Not testable")

    @Transactional
    override fun delete(id: Int) = error("Not testable")

    @Transactional
    override fun update(id: Int, update: EnrollmentService.EnrollmentUpdate): Enrollment {
        if (id !in ENROLLMENT_IDS) throw EntityNotFoundException(ExceptionEntity.ENROLLMENT)
        return MockUtils.mockEnrollment(id)
    }

    @Transactional
    override fun setSubjects(enrollmentId: Int, subjectIds: List<Int>) = error("Not testable")

    companion object {
        val ENROLLMENT_IDS = listOf(
            ENROLLMENT_ID,
            ENROLLMENT_WITHOUT_SUBJECTS_ID,
        )
    }

    override fun getAll() = ENROLLMENT_IDS.map { id -> MockUtils.mockEnrollment(id) }

    override fun getById(enrollmentId: Int) =
        if (enrollmentId in ENROLLMENT_IDS) MockUtils.mockEnrollment(enrollmentId)
        else null

    override fun getSubjects(enrollmentId: Int): EnrollmentService.QueryResult<out List<Subject>> {
        val enrollment = getById(enrollmentId)
            ?: return EnrollmentService.QueryResult.EnrollmentNotFound

        return EnrollmentService.QueryResult.Success(enrollment.subjects.toList())
    }

    override fun getSubject(
        enrollmentId: Int,
        subjectId: Int
    ): EnrollmentService.QueryResult<out Subject> {
        val enrollment = getById(enrollmentId)
            ?: return EnrollmentService.QueryResult.EnrollmentNotFound

        if (subjectId != SUBJECT_ID) {
            return EnrollmentService.QueryResult.SubjectNotFound
        }

        val subject = enrollment.subjects.find { it.id.value == subjectId }
            ?: return EnrollmentService.QueryResult.SubjectNotPartOfEnrollment(subjectId, enrollmentId)

        return EnrollmentService.QueryResult.Success(subject)
    }

    override fun getSubjectMembers(
        enrollmentId: Int,
        subjectId: Int,
        withStudents: Boolean,
    ): EnrollmentService.QueryResult<out Pair<List<Teacher>, List<Student>>> {
        return when (val result = getSubject(enrollmentId, subjectId)) {
            is EnrollmentService.QueryResult.EnrollmentNotFound,
            is EnrollmentService.QueryResult.SubjectNotFound,
            is EnrollmentService.QueryResult.SubjectNotPartOfEnrollment ->
                result

            else ->
                EnrollmentService.QueryResult.Success(
                    listOf(MockUtils.mockTeacher(TEACHER_ID)) to buildList {
                        if (withStudents) add(MockUtils.mockStudent(STUDENT_ID))
                    }
                )
        }
    }

    override fun getEnrolledCount(enrollmentId: Int): Int = 0

    override fun getUnenrolledMembers(
        enrollmentId: Int,
        groupId: Int,
        page: Int
    ): EnrollmentService.QueryResult<out Pair<List<Student>, Long>> {
        val enrollment = getById(enrollmentId)
            ?: return EnrollmentService.QueryResult.EnrollmentNotFound

        return EnrollmentService.QueryResult.Success(
            listOf(MockUtils.mockStudent(STUDENT_ID)) to 1L
        )
    }
}
