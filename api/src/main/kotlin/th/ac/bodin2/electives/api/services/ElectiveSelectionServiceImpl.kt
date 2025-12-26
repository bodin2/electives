package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.api.services.ElectiveSelectionService.*
import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Student.Companion.hasTeam
import th.ac.bodin2.electives.db.Student.Reference
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.models.StudentElectives
import th.ac.bodin2.electives.proto.api.UserType
import java.sql.Connection.TRANSACTION_SERIALIZABLE
import java.time.LocalDateTime

class ElectiveSelectionServiceImpl(
    private val usersService: UsersService,
    private val notificationsService: NotificationsService
) : ElectiveSelectionService {
    companion object {
        private val logger = LoggerFactory.getLogger(ElectiveSelectionServiceImpl::class.java)
    }

    // We're currently using SQLite, but in case if we ever switch, adding this isolation will be required
    // We want to prevent overbooking: Thread A reads (29/30) -> Thread B reads (29/30) -> A inserts (30/30) -> B inserts (31/30)
    // SERIALIZABLE will ensure that if two transactions try to do this at the same time, one of them will be fail and be retried by Exposed
    @CreatesTransaction
    override fun setStudentSelection(executorId: Int, userId: Int, electiveId: Int, subjectId: Int) =
        transaction(TRANSACTION_SERIALIZABLE) {
            maxAttempts = 3

            try {
                val student = Student.require(userId)
                val elective = Elective.require(electiveId)
                val subject = Subject.require(subjectId)

                if (student.id != executorId) {
                    if (usersService.getUserType(executorId) == UserType.TEACHER) {
                        val teacher = Teacher.require(executorId)
                        if (!Teacher.teachesSubject(teacher, subjectId)) {
                            return@transaction ModifySelectionResult.CannotModify(ModifySelectionStatus.FORBIDDEN)
                        }
                    } else {
                        return@transaction ModifySelectionResult.CannotModify(ModifySelectionStatus.FORBIDDEN)
                    }
                }

                // Acquire SQLite lock (will block instances of this transaction running in parallel)
                exec("BEGIN IMMEDIATE")

                when (val result = canEnrollInSubject(student, elective, subject)) {
                    CanEnrollStatus.CAN_ENROLL -> {}

                    else -> return@transaction ModifySelectionResult.CannotEnroll(result)
                }

                Student.setElectiveSelection(student, elective, subject)

                logger.info("Student elective selected, user: $userId, elective: $electiveId, subject: $subjectId, executor: $executorId")

                notificationsService.notifySubjectSelectionUpdate(
                    electiveId,
                    subjectId,
                    Subject.getEnrolledCount(subject, elective)
                )

                ModifySelectionResult.Success
            } catch (e: NotFoundException) {
                return@transaction e.tryHandling()
            }
        }

    @CreatesTransaction
    override fun deleteStudentSelection(executorId: Int, userId: Int, electiveId: Int): ModifySelectionResult =
        transaction {
            try {
                val student = Student.require(userId)
                val elective = Elective.require(electiveId)

                val selection = Student.getElectiveSelectionId(student, elective)
                    ?: return@transaction ModifySelectionResult.CannotModify(ModifySelectionStatus.NOT_ENROLLED)

                if (student.id != executorId) {
                    if (usersService.getUserType(executorId) == UserType.TEACHER) {
                        val teacher = Teacher.require(executorId)
                        if (!Teacher.teachesSubject(teacher, selection.value)) {
                            return@transaction ModifySelectionResult.CannotModify(ModifySelectionStatus.FORBIDDEN)
                        }
                    } else {
                        return@transaction ModifySelectionResult.CannotModify(ModifySelectionStatus.FORBIDDEN)
                    }
                }


                Student.removeElectiveSelection(student, elective)

                logger.info("Student elective removed, user: $userId, elective: $electiveId, executor: $executorId")

                notificationsService.notifySubjectSelectionUpdate(
                    electiveId,
                    selection.value,
                    Subject.getEnrolledCount(selection, elective)
                )

                ModifySelectionResult.Success
            } catch (e: NotFoundException) {
                return@transaction e.tryHandling()
            }
        }

    private fun NotFoundException.tryHandling(): ModifySelectionResult {
        return when (entity) {
            NotFoundEntity.ELECTIVE,
            NotFoundEntity.SUBJECT,
            NotFoundEntity.STUDENT -> {
                ModifySelectionResult.NotFound(entity)
            }

            // Should never throw, we already check teacher existence in setStudentSelection
            NotFoundEntity.TEACHER -> throw IllegalStateException("Unreachable TEACHER NotFoundException")

            // Should never throw, we already try to get the selection above in deleteStudentSelection
            NotFoundEntity.ELECTIVE_SELECTION -> throw IllegalStateException("Unreachable ELECTIVE_SELECTION NotFoundException")

            else -> throw this
        }
    }

    /**
     * Checks if the student can enroll in the specified subject of the elective by:
     *
     * 1. Checking if the elective has started/ended.
     * 2. Checking if the subject is part of the elective (nice try).
     * 3. Checking if the student is already enrolled in another subject of the same elective.
     * 4. Checking if the student is part of the elective's team (if any).
     * 5. Checking if the student is part of the subject's team (if any).
     * 6. Checking if the subject is full.
     */
    fun canEnrollInSubject(
        student: Reference,
        elective: Elective.Reference,
        subject: Subject.Reference
    ): CanEnrollStatus {
        // Check elective date range
        val (startDate, endDate) = Elective.getEnrollmentDateRange(elective)
        val now = LocalDateTime.now()
        if (startDate != null && startDate.isBefore(now)) return CanEnrollStatus.NOT_IN_ELECTIVE_DATE_RANGE
        if (endDate != null && endDate.isAfter(now)) return CanEnrollStatus.NOT_IN_ELECTIVE_DATE_RANGE

        if (!Subject.isPartOfElective(subject, elective)) return CanEnrollStatus.SUBJECT_NOT_IN_ELECTIVE

        // Check if the student is already enrolled in another subject of the same elective
        val alreadyEnrolled = StudentElectives
            .selectAll()
            .where { (StudentElectives.student eq student.id) and (StudentElectives.elective eq elective.id) }
            .empty().not()

        if (alreadyEnrolled) return CanEnrollStatus.ALREADY_ENROLLED

        // Check if the student is part of the elective's team (if any)
        val electiveTeamId = Elective.getTeamId(elective)
        if (electiveTeamId != null && !hasTeam(student, electiveTeamId))
            return CanEnrollStatus.NOT_IN_ELECTIVE_TEAM

        // Check if the student is part of the subject's team (if any)
        val subjectTeamId = Subject.getTeamId(subject)
        if (subjectTeamId != null && !hasTeam(student, subjectTeamId))
            return CanEnrollStatus.NOT_IN_SUBJECT_TEAM

        if (Subject.isFull(subject, elective))
            return CanEnrollStatus.SUBJECT_FULL

        return CanEnrollStatus.CAN_ENROLL
    }
}