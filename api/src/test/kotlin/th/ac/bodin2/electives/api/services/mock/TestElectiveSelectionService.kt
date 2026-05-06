package th.ac.bodin2.electives.api.services.mock

import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.MockUtils
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.ElectiveSelectionService
import th.ac.bodin2.electives.api.services.ElectiveSelectionService.ModifySelectionResult
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ELECTIVE_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.STUDENT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.db.Subject

// Actual logic should be tested in the actual implementation
// We just want to test the response handling in the API layer
var testElectiveSelectionServiceResponse: ModifySelectionResult = ModifySelectionResult.Success

class TestElectiveSelectionService : ElectiveSelectionService {
    @Transactional
    override fun forceSetAllStudentSelections(studentId: UInt, selections: Map<UInt, UInt>) = error("Not testable")

    @Transactional
    override suspend fun setStudentSelection(
        executor: UsersService.SessionUser,
        studentId: UInt,
        electiveId: UInt,
        subjectId: UInt,
    ) = testElectiveSelectionServiceResponse

    @Transactional
    override suspend fun deleteStudentSelection(
        executor: UsersService.SessionUser,
        studentId: UInt,
        electiveId: UInt,
    ) = testElectiveSelectionServiceResponse

    @Transactional
    override fun getStudentSelections(studentId: UInt): Map<UInt, Subject> {
        if (studentId != STUDENT_ID) throw EntityNotFoundException(ExceptionEntity.STUDENT)
        return mapOf(ELECTIVE_ID to MockUtils.mockSubject(SUBJECT_ID))
    }
}