package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.db.Subject

interface EnrollmentSelectionService {
    /**
     * Sets all subject selections for a student. No permission or seat checks are performed.
     *
     * @param studentId The ID of the student.
     * @param selections A map of enrollment IDs to selected subject IDs.
     *
     * @throws th.ac.bodin2.electives.EntityNotFoundException if the student, enrollment, or subject does not exist.
     * @throws IllegalArgumentException if the subject is not part of the enrollment.
     */
    @Transactional
    fun forceSetAllStudentSelections(
        studentId: Int,
        selections: Map<Int, Int>,
    )

    /**
     * Sets the subject selection for a student in a given enrollment.
     *
     * @param executor The user performing the operation.
     *
     * @throws th.ac.bodin2.electives.EntityNotFoundException if the student, enrollment, or subject does not exist.
     */
    @Transactional
    suspend fun setStudentSelection(
        executor: UsersService.SessionUser,
        studentId: Int,
        enrollmentId: Int,
        subjectId: Int
    ): ModifySelectionResult

    /**
     * Deletes the subject selection for a student in a given enrollment.
     *
     * @param executor The user performing the operation.
     *
     * @throws th.ac.bodin2.electives.EntityNotFoundException if the student or enrollment does not exist.
     */
    @Transactional
    suspend fun deleteStudentSelection(
        executor: UsersService.SessionUser,
        studentId: Int,
        enrollmentId: Int
    ): ModifySelectionResult

    /**
     * Gets all subject selections for a student.
     *
     * @param studentId The ID of the student.
     * @return A map of enrollment IDs to selected subject IDs.
     *
     * @throws th.ac.bodin2.electives.EntityNotFoundException if the student does not exist.
     */
    fun getStudentSelections(studentId: Int): Map<Int, Subject>

    enum class CanEnrollStatus {
        CAN_ENROLL,

        /**
         * The subject is not part of the enrollment.
         */
        SUBJECT_NOT_IN_ENROLLMENT,

        /**
         * The student is already enrolled in a subject for this enrollment.
         */
        ALREADY_ENROLLED,

        /**
         * The student is not part of the enrollment's group.
         */
        NOT_IN_ENROLLMENT_GROUP,

        /**
         * The student is not part of the subject's group.
         */
        NOT_IN_SUBJECT_GROUP,

        /**
         * The subject is already full.
         */
        SUBJECT_FULL,

        /**
         * The current date is not within the allowed enrollment date range.
         */
        NOT_IN_ENROLLMENT_DATE_RANGE,
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
