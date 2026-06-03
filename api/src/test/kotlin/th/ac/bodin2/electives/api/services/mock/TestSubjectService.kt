package th.ac.bodin2.electives.api.services.mock

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.MockUtils
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.SubjectService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.TEACHER_ID

object TestSubjectService {
    val SUBJECT_IDS = listOf(SUBJECT_ID)

    @OptIn(Transactional::class)
    operator fun invoke(): SubjectService = mockk(relaxed = true) {
        coEvery { update(any(), any()) } answers {
            val id = firstArg<Int>()
            if (id !in SUBJECT_IDS) throw EntityNotFoundException(ExceptionEntity.SUBJECT)
            MockUtils.mockSubject(id)
        }

        every { getAll() } answers { SUBJECT_IDS.map { id -> MockUtils.mockSubject(id) } }

        every { getById(any()) } answers {
            val subjectId = firstArg<Int>()
            if (subjectId in SUBJECT_IDS) MockUtils.mockSubject(subjectId) else null
        }

        every { getEnrollmentIds(any()) } returns listOf(ENROLLMENT_ID)

        every { getTeacherSubjects(any()) } answers {
            val teacherId = firstArg<Int>()
            if (teacherId != TEACHER_ID) throw EntityNotFoundException(ExceptionEntity.TEACHER)
            mapOf(ENROLLMENT_ID to MockUtils.mockSubject(SUBJECT_ID))
        }
    }
}
