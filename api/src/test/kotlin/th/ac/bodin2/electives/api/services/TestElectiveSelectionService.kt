package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.api.services.ElectiveSelectionService.ModifySelectionResult
import th.ac.bodin2.electives.api.services.TestServiceConstants.ELECTIVE_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.db.Subject

// Actual logic should be tested in the actual implementation
// We just want to test the response handling in the API layer
var testElectiveSelectionServiceResponse: ModifySelectionResult = ModifySelectionResult.Success

class TestElectiveSelectionService : ElectiveSelectionService {
    @CreatesTransaction
    override fun forceSetAllStudentSelections(userId: Int, selections: Map<Int, Int>) = error("Not testable")

    @CreatesTransaction
    override fun setStudentSelection(
        executorId: Int,
        userId: Int,
        electiveId: Int,
        subjectId: Int,
    ) = testElectiveSelectionServiceResponse

    @CreatesTransaction
    override fun deleteStudentSelection(
        executorId: Int,
        userId: Int,
        electiveId: Int,
    ) = testElectiveSelectionServiceResponse

    @CreatesTransaction
    override fun getStudentSelections(userId: Int): Map<Int, Subject> {
        if (userId != STUDENT_ID) throw NotFoundException(NotFoundEntity.STUDENT)
        return mapOf(ELECTIVE_ID to MockUtils.mockSubject(SUBJECT_ID))
    }
}