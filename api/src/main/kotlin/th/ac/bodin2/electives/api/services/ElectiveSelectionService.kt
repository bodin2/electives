package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.db.Subject

interface ElectiveSelectionService {
    /**
     * Sets all subject selections for a student. No permission or seat checks are performed.
     *
     * @param userId The ID of the student.
     * @param selections A map of elective IDs to selected subject IDs.
     *
     * @throws th.ac.bodin2.electives.NotFoundException if the student, elective, or subject does not exist.
     * @throws IllegalArgumentException if the subject is not part of the elective.
     */
    @CreatesTransaction
    fun forceSetAllStudentSelections(
        userId: Int,
        selections: Map<Int, Int>,
    )

    /**
     * Sets the subject selection for a student in a given elective.
     *
     * @param executorId The ID of the user performing the operation.
     *
     * @throws th.ac.bodin2.electives.NotFoundException if the student, elective, or subject does not exist.
     */
    @CreatesTransaction
    fun setStudentSelection(executorId: Int, userId: Int, electiveId: Int, subjectId: Int): ModifySelectionResult

    /**
     * Deletes the subject selection for a student in a given elective.
     *
     * @param executorId The ID of the user performing the operation.
     *
     * @throws th.ac.bodin2.electives.NotFoundException if the student or elective does not exist.
     */
    @CreatesTransaction
    fun deleteStudentSelection(executorId: Int, userId: Int, electiveId: Int): ModifySelectionResult

    /**
     * Gets all subject selections for a student.
     *
     * @param userId The ID of the student.
     * @return A map of elective IDs to selected subject IDs.
     *
     * @throws th.ac.bodin2.electives.NotFoundException if the student does not exist.
     */
    fun getStudentSelections(userId: Int): Map<Int, Subject>

    enum class CanEnrollStatus {
        CAN_ENROLL,

        /**
         * The subject is not part of the elective.
         */
        SUBJECT_NOT_IN_ELECTIVE,

        /**
         * The student is already enrolled in a subject for this elective.
         */
        ALREADY_ENROLLED,

        /**
         * The student is not part of the elective's team.
         */
        NOT_IN_ELECTIVE_TEAM,

        /**
         * The student is not part of the subject's team.
         */
        NOT_IN_SUBJECT_TEAM,

        /**
         * The subject is already full.
         */
        SUBJECT_FULL,

        /**
         * The current date is not within the allowed enrollment date range.
         */
        NOT_IN_ELECTIVE_DATE_RANGE,
    }

    sealed class ModifySelectionResult {
        data object Success : ModifySelectionResult()

        data class NotFound(val entity: ExceptionEntity) : ModifySelectionResult()
        data class CannotEnroll(val status: CanEnrollStatus) : ModifySelectionResult()
        data class CannotModify(val status: ModifySelectionStatus) : ModifySelectionResult()
    }

    enum class ModifySelectionStatus {
        /**
         * If the user is a student, they do not have permission to modify another student's selection.
         * If the user is a teacher enrolling for a student, they aren't teaching the selected subject.
         */
        FORBIDDEN,

        NOT_ENROLLED,
    }
}