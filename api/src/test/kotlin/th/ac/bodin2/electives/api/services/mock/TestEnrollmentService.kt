package th.ac.bodin2.electives.api.services.mock

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.MockUtils
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.EnrollmentService
import th.ac.bodin2.electives.api.services.EnrollmentService.QueryResult
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_WITHOUT_SUBJECTS_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.TEACHER_ID
import th.ac.bodin2.electives.db.Subject

object TestEnrollmentService {
    val ENROLLMENT_IDS = listOf(
        ENROLLMENT_ID,
        ENROLLMENT_WITHOUT_SUBJECTS_ID,
    )

    @OptIn(Transactional::class)
    operator fun invoke(): EnrollmentService {
        fun resolveEnrollment(enrollmentId: Int) =
            if (enrollmentId in ENROLLMENT_IDS) MockUtils.mockEnrollment(enrollmentId) else null

        fun resolveSubject(enrollmentId: Int, subjectId: Int): QueryResult<out Subject> {
            val enrollment = resolveEnrollment(enrollmentId)
                ?: return QueryResult.EnrollmentNotFound

            if (subjectId != SUBJECT_ID) {
                return QueryResult.SubjectNotFound
            }

            val subject = enrollment.subjects.find { it.id.value == subjectId }
                ?: return QueryResult.SubjectNotPartOfEnrollment(subjectId, enrollmentId)

            return QueryResult.Success(subject)
        }

        return mockk(relaxed = true) {
            coEvery { update(any(), any()) } answers {
                val id = firstArg<Int>()
                if (id !in ENROLLMENT_IDS) throw EntityNotFoundException(ExceptionEntity.ENROLLMENT)
                MockUtils.mockEnrollment(id)
            }

            every { getAll() } answers { ENROLLMENT_IDS.map { id -> MockUtils.mockEnrollment(id) } }

            every { getById(any()) } answers { resolveEnrollment(firstArg()) }

            every { getSubjects(any()) } answers {
                val enrollmentId = firstArg<Int>()
                val enrollment = resolveEnrollment(enrollmentId)
                    ?: return@answers QueryResult.EnrollmentNotFound

                QueryResult.Success(enrollment.subjects.toList())
            }

            every { getSubject(any(), any()) } answers {
                resolveSubject(firstArg(), secondArg())
            }

            every { getSubjectMembers(any(), any(), any()) } answers {
                val enrollmentId = firstArg<Int>()
                val subjectId = secondArg<Int>()
                val withStudents = thirdArg<Boolean>()
                when (val result = resolveSubject(enrollmentId, subjectId)) {
                    is QueryResult.EnrollmentNotFound,
                    is QueryResult.SubjectNotFound,
                    is QueryResult.SubjectNotPartOfEnrollment ->
                        result

                    else ->
                        QueryResult.Success(
                            listOf(MockUtils.mockTeacher(TEACHER_ID)) to buildList {
                                if (withStudents) add(MockUtils.mockStudent(STUDENT_ID))
                            }
                        )
                }
            }

            every { getEnrolledCount(any()) } returns 0

            every { getUnenrolledMembers(any(), any(), any()) } answers {
                val enrollmentId = firstArg<Int>()
                resolveEnrollment(enrollmentId)
                    ?: return@answers QueryResult.EnrollmentNotFound

                QueryResult.Success(
                    listOf(MockUtils.mockStudent(STUDENT_ID)) to 1L
                )
            }
        }
    }
}
