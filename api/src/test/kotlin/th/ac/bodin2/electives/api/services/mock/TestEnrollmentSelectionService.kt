package th.ac.bodin2.electives.api.services.mock

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.MockUtils
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.EnrollmentSelectionService
import th.ac.bodin2.electives.api.services.EnrollmentSelectionService.ModifySelectionResult
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_ID

// Actual logic should be tested in the actual implementation
// We just want to test the response handling in the API layer
var testEnrollmentSelectionServiceResponse: ModifySelectionResult = ModifySelectionResult.Success

object TestEnrollmentSelectionService {
    @OptIn(Transactional::class)
    operator fun invoke(): EnrollmentSelectionService = mockk(relaxed = true) {
        coEvery { setStudentSelection(any(), any(), any(), any()) } answers { testEnrollmentSelectionServiceResponse }

        coEvery { deleteStudentSelection(any(), any(), any()) } answers { testEnrollmentSelectionServiceResponse }

        every { getStudentSelections(any()) } answers {
            val studentId = firstArg<Int>()
            if (studentId != STUDENT_ID) throw EntityNotFoundException(ExceptionEntity.STUDENT)
            mapOf(ENROLLMENT_ID to MockUtils.mockSubject(SUBJECT_ID))
        }
    }
}
