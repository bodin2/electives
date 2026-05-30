package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.EnrollmentSelectionService.*
import th.ac.bodin2.electives.api.utils.dbQuery
import th.ac.bodin2.electives.db.Enrollment
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Student.Companion.hasGroup
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.models.StudentClasses
import th.ac.bodin2.electives.db.models.Subjects
import th.ac.bodin2.electives.proto.api.UserType
import java.sql.Connection.TRANSACTION_SERIALIZABLE
import java.time.LocalDateTime

class EnrollmentSelectionServiceImpl(private val notificationsService: NotificationsService) :
    EnrollmentSelectionService {
    companion object {
        private val logger = LoggerFactory.getLogger(EnrollmentSelectionServiceImpl::class.java)
    }

    // We want to prevent overbooking: Thread A reads (29/30) -> Thread B reads (29/30) -> A inserts (30/30) -> B inserts (31/30)
    // SERIALIZABLE will ensure that if two transactions try to do this at the same time, one of them will be fail.
    @Transactional
    override suspend fun setStudentSelection(
        executor: UsersService.SessionUser,
        studentId: Int,
        enrollmentId: Int,
        subjectId: Int
    ): ModifySelectionResult {
        var onSuccess: (suspend () -> Unit)? = null

        val result = dbQuery(transactionIsolation = TRANSACTION_SERIALIZABLE) {
            maxAttempts = 3

            try {
                Student.assertExists(studentId)
                Enrollment.assertExists(enrollmentId)
                Subject.assertExists(subjectId)

                val executorIsSomeoneElse = studentId != executor.id
                if (executorIsSomeoneElse) {
                    if (executor.type == UserType.TEACHER) {
                        if (!Teacher.teachesSubject(executor.id, subjectId, enrollmentId)) {
                            return@dbQuery ModifySelectionResult.CannotModify(ModifySelectionStatus.FORBIDDEN)
                        }
                    } else if (executor.type != UserType.ADMIN) {
                        return@dbQuery ModifySelectionResult.CannotModify(ModifySelectionStatus.FORBIDDEN)
                    }
                }

                // By the time we reach here, bypassDateCheck will only be true if the executor is an admin
                val bypassDateCheck = executor.type == UserType.ADMIN
                when (val result = canEnrollInSubject(studentId, enrollmentId, subjectId, bypassDateCheck)) {
                    CanEnrollStatus.CAN_ENROLL -> {}

                    else -> return@dbQuery ModifySelectionResult.CannotEnroll(result)
                }

                val currentCount = wrapAsExpression<Long>(
                    StudentClasses
                        .select(StudentClasses.student.count())
                        .where {
                            (StudentClasses.enrollment eq enrollmentId) and
                                    (StudentClasses.subject eq subjectId)
                        })

                val capacity = wrapAsExpression<Int>(
                    Subjects
                        .select(Subjects.capacity)
                        .where { Subjects.id eq subjectId })

                val inserted = StudentClasses.insertIgnore(
                    Subjects
                        .select(intParam(studentId), intParam(enrollmentId), intParam(subjectId))
                        .where {
                            (Subjects.id eq subjectId) and (currentCount less capacity.castTo(LongColumnType()))
                        },
                )

                if (inserted == 0 || inserted == null) {
                    // Not inserted, subject is full
                    return@dbQuery ModifySelectionResult.CannotEnroll(
                        CanEnrollStatus.SUBJECT_FULL
                    )
                }

                onSuccess = {
                    if (logger.isDebugEnabled) logger.debug("Student enrolled in subject, user: $studentId, enrollment: $enrollmentId, subject: $subjectId, executor: ${executor.id}")

                    notificationsService.notifySubjectSelectionUpdate(
                        enrollmentId,
                        subjectId,
                        dbQuery { Subject.getEnrolledCount(subjectId, enrollmentId) }
                    )
                }

                return@dbQuery ModifySelectionResult.Success
            } catch (e: EntityNotFoundException) {
                return@dbQuery e.tryHandling()
            }
        }

        onSuccess?.invoke()

        return result
    }

    @Transactional
    override suspend fun deleteStudentSelection(
        executor: UsersService.SessionUser,
        studentId: Int,
        enrollmentId: Int
    ): ModifySelectionResult = dbQuery {
        try {
            Student.assertExists(studentId)
            Enrollment.assertExists(enrollmentId)

            val selection = Student.getEnrollmentSelectionId(studentId, enrollmentId)
                ?: return@dbQuery ModifySelectionResult.CannotModify(ModifySelectionStatus.NOT_ENROLLED)

            if (studentId != executor.id) {
                if (executor.type == UserType.TEACHER) {
                    if (!Teacher.teachesSubject(executor.id, selection.value, enrollmentId)) {
                        return@dbQuery ModifySelectionResult.CannotModify(ModifySelectionStatus.FORBIDDEN)
                    }
                } else if (executor.type != UserType.ADMIN) {
                    return@dbQuery ModifySelectionResult.CannotModify(ModifySelectionStatus.FORBIDDEN)
                }
            }

            val bypassDateCheck = executor.type == UserType.ADMIN
            checkDateRange(enrollmentId, bypassDateCheck)?.let {
                return@dbQuery ModifySelectionResult.CannotEnroll(it)
            }

            Student.removeEnrollmentSelection(studentId, enrollmentId)

            if (logger.isDebugEnabled) logger.debug("Student enrollment selection removed, user: $studentId, enrollment: $enrollmentId, executor: ${executor.id}")

            notificationsService.notifySubjectSelectionUpdate(
                enrollmentId,
                selection.value,
                Subject.getEnrolledCount(selection.value, enrollmentId)
            )

            ModifySelectionResult.Success
        } catch (e: EntityNotFoundException) {
            return@dbQuery e.tryHandling()
        }
    }

    private fun EntityNotFoundException.tryHandling(): ModifySelectionResult {
        return when (entity) {
            ExceptionEntity.ENROLLMENT,
            ExceptionEntity.SUBJECT,
            ExceptionEntity.STUDENT -> {
                ModifySelectionResult.NotFound(entity)
            }

            // Should never throw, we already check teacher existence in setStudentSelection
            ExceptionEntity.TEACHER,
                // Should never throw, we already try to get the selection above in deleteStudentSelection
            ExceptionEntity.ENROLLMENT_SELECTION -> throw IllegalStateException("Unreachable ${entity.name} EntityNotFoundException")

            else -> throw this
        }
    }

    override fun getStudentSelections(studentId: Int): Map<Int, Subject> {
        Student.assertExists(studentId)
        return buildMap { Student.getAllEnrollmentSelections(studentId).forEach { put(it.first, it.second) } }
    }

    private fun checkDateRange(enrollmentId: Int, bypass: Boolean): CanEnrollStatus? {
        val (startDate, endDate) = Enrollment.getEnrollmentDateRange(enrollmentId)
        val now = LocalDateTime.now()
        if (!bypass) {
            if (startDate != null && now.isBefore(startDate)) return CanEnrollStatus.NOT_IN_ENROLLMENT_DATE_RANGE
            if (endDate != null && now.isAfter(endDate)) return CanEnrollStatus.NOT_IN_ENROLLMENT_DATE_RANGE
        }
        return null
    }

    /**
     * Checks if the student can enroll in the specified subject of the enrollment by:
     *
     * 1. Checking if the enrollment has started/ended.
     * 2. Checking if the subject is part of the enrollment (nice try).
     * 3. Checking if the student is already enrolled in another subject of the same enrollment.
     * 4. Checking if the student is part of the enrollment's group (if any).
     * 5. Checking if the student is part of the subject's group (if any).
     *
     * **Subject capacity is not checked here and should be handled by the caller within a transaction with proper isolation level to prevent TOCTOU issues.**
     */
    private fun canEnrollInSubject(
        studentId: Int,
        enrollmentId: Int,
        subjectId: Int,
        bypassDateCheck: Boolean,
    ): CanEnrollStatus {
        // Check enrollment date range
        checkDateRange(enrollmentId, bypassDateCheck)?.let { return it }

        if (!Subject.isPartOfEnrollment(subjectId, enrollmentId)) return CanEnrollStatus.SUBJECT_NOT_IN_ENROLLMENT

        // Check if the student is already enrolled in another subject of the same enrollment
        // UNSAFE FROM TOCTOU by its own!!! This is handled by the unique index on (student, enrollment) in the StudentClasses table and the SERIALIZABLE transaction isolation level!
        val alreadyEnrolled = StudentClasses
            .selectAll()
            .where { (StudentClasses.student eq studentId) and (StudentClasses.enrollment eq enrollmentId) }
            .empty().not()

        if (alreadyEnrolled) return CanEnrollStatus.ALREADY_ENROLLED

        // Check if the student is part of the enrollment's group (if any)
        val enrollmentGroupId = Enrollment.getGroupId(enrollmentId)
        if (enrollmentGroupId != null && !hasGroup(studentId, enrollmentGroupId))
            return CanEnrollStatus.NOT_IN_ENROLLMENT_GROUP

        // Check if the student is part of the subject's group (if any)
        val subjectGroupId = Subject.getGroupId(subjectId)
        if (subjectGroupId != null && !hasGroup(studentId, subjectGroupId))
            return CanEnrollStatus.NOT_IN_SUBJECT_GROUP

        return CanEnrollStatus.CAN_ENROLL
    }
}
