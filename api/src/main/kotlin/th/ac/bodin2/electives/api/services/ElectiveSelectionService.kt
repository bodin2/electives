package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.api.annotations.CreatesTransaction

interface ElectiveSelectionService {
    /**
     * Sets the subject selection for a student in a given elective.
     *
     * @param executorId The ID of the user performing the operation.
     */
    @CreatesTransaction
    fun setStudentSelection(executorId: Int, userId: Int, electiveId: Int, subjectId: Int): ModifySelectionResult

    /**
     * Deletes the subject selection for a student in a given elective.
     *
     * @param executorId The ID of the user performing the operation.
     */
    @CreatesTransaction
    fun deleteStudentSelection(executorId: Int, userId: Int, electiveId: Int): ModifySelectionResult

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
        object Success : ModifySelectionResult()
        class NotFound(val entity: NotFoundEntity) : ModifySelectionResult()
        class CannotEnroll(val status: CanEnrollStatus) : ModifySelectionResult()
        class CannotModify(val status: ModifySelectionStatus) : ModifySelectionResult()
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