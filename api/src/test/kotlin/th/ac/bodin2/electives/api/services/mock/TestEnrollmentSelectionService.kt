package th.ac.bodin2.electives.api.services.mock

import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.MockUtils
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.EnrollmentSelectionService
import th.ac.bodin2.electives.api.services.EnrollmentSelectionService.ModifySelectionResult
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.db.Subject

// Actual logic should be tested in the actual implementation
// We just want to test the response handling in the API layer
var testEnrollmentSelectionServiceResponse: ModifySelectionResult = ModifySelectionResult.Success

class TestEnrollmentSelectionService : EnrollmentSelectionService {
    @Transactional
    override fun forceSetAllStudentSelections(studentId: Int, selections: Map<Int, Int>) = error("Not testable")

    @Transactional
    override suspend fun setStudentSelection(
        executor: UsersService.SessionUser,
        studentId: Int,
        enrollmentId: Int,
        subjectId: Int,
    ) = testEnrollmentSelectionServiceResponse

    @Transactional
    override suspend fun deleteStudentSelection(
        executor: UsersService.SessionUser,
        studentId: Int,
        enrollmentId: Int,
    ) = testEnrollmentSelectionServiceResponse

    @Transactional
    override fun getStudentSelections(studentId: Int): Map<Int, Subject> {
        if (studentId != STUDENT_ID) throw EntityNotFoundException(ExceptionEntity.STUDENT)
        return mapOf(ENROLLMENT_ID to MockUtils.mockSubject(SUBJECT_ID))
    }
}
