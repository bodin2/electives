package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.ElectiveSelectionService.*
import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Student.Companion.hasTeam
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.models.StudentElectives
import th.ac.bodin2.electives.db.models.Subjects
import th.ac.bodin2.electives.proto.api.UserType
import java.sql.Connection
import java.sql.Connection.TRANSACTION_SERIALIZABLE
import java.time.LocalDateTime

class ElectiveSelectionServiceImpl(private val notificationsService: NotificationsService) : ElectiveSelectionService {
    companion object {
        private val logger = LoggerFactory.getLogger(ElectiveSelectionServiceImpl::class.java)
        private val INSERT_SELECTION_QUERY = """
            INSERT INTO ${StudentElectives.tableName}(
              ${StudentElectives.student.name},
              ${StudentElectives.elective.name},
              ${StudentElectives.subject.name}
            )
            SELECT ?, ?, ?
            WHERE (
              SELECT COUNT(*)
              FROM ${StudentElectives.tableName}
              WHERE ${StudentElectives.elective.name} = ? AND ${StudentElectives.subject.name} = ?
            ) < (
              SELECT ${Subjects.capacity.name}
              FROM ${Subjects.tableName}
              WHERE id = ?
            )
        """.trimIndent()
    }

    @Transactional
    override fun forceSetAllStudentSelections(studentId: Int, selections: Map<Int, Int>) {
        transaction {
            Student.assertExists(studentId)

            val selections = selections.map { (electiveId, subjectId) ->
                Subject.assertExists(subjectId)
                Elective.assertExists(electiveId)

                if (!Subject.isPartOfElective(subjectId, electiveId)) {
                    throw IllegalArgumentException("Subject $subjectId is not part of elective $electiveId")
                }

                electiveId to subjectId
            }

            StudentElectives.deleteWhere { StudentElectives.student eq studentId }
            StudentElectives.batchInsert(selections) { (electiveId, subjectId) ->
                this[StudentElectives.student] = studentId
                this[StudentElectives.elective] = electiveId
                this[StudentElectives.subject] = subjectId
            }
        }
    }

    // We're currently using SQLite, which locks during writes, so technically all of this code to prevent TOCTOU doesn't really matter.
    // But in case if we ever switch databases, this will be useful.

    // We want to prevent overbooking: Thread A reads (29/30) -> Thread B reads (29/30) -> A inserts (30/30) -> B inserts (31/30)
    // SERIALIZABLE will ensure that if two transactions try to do this at the same time, one of them will be fail.
    @Transactional
    override suspend fun setStudentSelection(
        executor: UsersService.SessionUser,
        studentId: Int,
        electiveId: Int,
        subjectId: Int
    ): ModifySelectionResult {
        var onSuccess: (() -> Unit)? = null

        val result = transaction(transactionIsolation = TRANSACTION_SERIALIZABLE) {
            maxAttempts = 3

            try {
                Student.assertExists(studentId)
                Elective.assertExists(electiveId)
                Subject.assertExists(subjectId)

                val executorIsSomeoneElse = studentId != executor.id
                if (executorIsSomeoneElse) {
                    if (executor.type == UserType.TEACHER) {
                        if (!Teacher.teachesSubject(executor.id, subjectId)) {
                            return@transaction ModifySelectionResult.CannotModify(ModifySelectionStatus.FORBIDDEN)
                        }
                    } else {
                        return@transaction ModifySelectionResult.CannotModify(ModifySelectionStatus.FORBIDDEN)
                    }
                }

                // By the time we reach here, bypassDateCheck will only be true if the executor is a teacher
                when (val result = canEnrollInSubject(studentId, electiveId, subjectId, executorIsSomeoneElse)) {
                    CanEnrollStatus.CAN_ENROLL -> {}

                    else -> return@transaction ModifySelectionResult.CannotEnroll(result)
                }

                (connection.connection as Connection).prepareStatement(INSERT_SELECTION_QUERY).use { ps ->
                    ps.setInt(1, studentId)
                    ps.setInt(2, electiveId)
                    ps.setInt(3, subjectId)

                    ps.setInt(4, electiveId)
                    ps.setInt(5, subjectId)

                    ps.setInt(6, subjectId)

                    val inserted = ps.executeUpdate()
                    if (inserted == 0) {
                        logger.debug("Student elective selection failed due to full subject, user: $studentId, elective: $electiveId, subject: $subjectId, executor: ${executor.id}")

                        // Not inserted, subject is full
                        return@transaction ModifySelectionResult.CannotEnroll(
                            CanEnrollStatus.SUBJECT_FULL
                        )
                    }
                }

                onSuccess = {
                    logger.debug("Student elective selected, user: $studentId, elective: $electiveId, subject: $subjectId, executor: ${executor.id}")

                    notificationsService.notifySubjectSelectionUpdate(
                        electiveId,
                        subjectId,
                        transaction { Subject.getEnrolledCount(subjectId, electiveId) }
                    )
                }

                return@transaction ModifySelectionResult.Success
            } catch (e: EntityNotFoundException) {
                return@transaction e.tryHandling()
            }
        }

        onSuccess?.invoke()

        return result
    }

    @Transactional
    override suspend fun deleteStudentSelection(
        executor: UsersService.SessionUser,
        studentId: Int,
        electiveId: Int
    ): ModifySelectionResult =
        transaction {
            try {
                Student.assertExists(studentId)
                Elective.assertExists(electiveId)

                val selection = Student.getElectiveSelectionId(studentId, electiveId)
                    ?: return@transaction ModifySelectionResult.CannotModify(ModifySelectionStatus.NOT_ENROLLED)

                if (studentId != executor.id) {
                    if (executor.type == UserType.TEACHER) {
                        if (!Teacher.teachesSubject(executor.id, selection.value)) {
                            return@transaction ModifySelectionResult.CannotModify(ModifySelectionStatus.FORBIDDEN)
                        }
                    } else {
                        return@transaction ModifySelectionResult.CannotModify(ModifySelectionStatus.FORBIDDEN)
                    }
                }

                Student.removeElectiveSelection(studentId, electiveId)

                logger.debug("Student elective removed, user: $studentId, elective: $electiveId, executor: ${executor.id}")

                notificationsService.notifySubjectSelectionUpdate(
                    electiveId,
                    selection.value,
                    Subject.getEnrolledCount(selection.value, electiveId)
                )

                ModifySelectionResult.Success
            } catch (e: EntityNotFoundException) {
                return@transaction e.tryHandling()
            }
        }

    private fun EntityNotFoundException.tryHandling(): ModifySelectionResult {
        return when (entity) {
            ExceptionEntity.ELECTIVE,
            ExceptionEntity.SUBJECT,
            ExceptionEntity.STUDENT -> {
                ModifySelectionResult.NotFound(entity)
            }

            // Should never throw, we already check teacher existence in setStudentSelection
            ExceptionEntity.TEACHER,
                // Should never throw, we already try to get the selection above in deleteStudentSelection
            ExceptionEntity.ELECTIVE_SELECTION -> throw IllegalStateException("Unreachable ${entity.name} EntityNotFoundException")

            else -> throw this
        }
    }

    override fun getStudentSelections(studentId: Int): Map<Int, Subject> {
        Student.assertExists(studentId)
        return buildMap { Student.getAllElectiveSelections(studentId).forEach { put(it.first, it.second) } }
    }

    /**
     * Checks if the student can enroll in the specified subject of the elective by:
     *
     * 1. Checking if the elective has started/ended.
     * 2. Checking if the subject is part of the elective (nice try).
     * 3. Checking if the student is already enrolled in another subject of the same elective.
     * 4. Checking if the student is part of the elective's team (if any).
     * 5. Checking if the student is part of the subject's team (if any).
     *
     * **Subject capacity is not checked here and should be handled by the caller within a transaction with proper isolation level to prevent TOCTOU issues.**
     */
    private fun canEnrollInSubject(
        studentId: Int,
        electiveId: Int,
        subjectId: Int,
        bypassDateCheck: Boolean,
    ): CanEnrollStatus {
        // Check elective date range
        val (startDate, endDate) = Elective.getEnrollmentDateRange(electiveId)
        val now = LocalDateTime.now()
        if (!bypassDateCheck) {
            if (startDate != null && now.isBefore(startDate)) return CanEnrollStatus.NOT_IN_ELECTIVE_DATE_RANGE
            if (endDate != null && now.isAfter(endDate)) return CanEnrollStatus.NOT_IN_ELECTIVE_DATE_RANGE
        }

        if (!Subject.isPartOfElective(subjectId, electiveId)) return CanEnrollStatus.SUBJECT_NOT_IN_ELECTIVE

        // Check if the student is already enrolled in another subject of the same elective
        // UNSAFE FROM TOCTOU by its own!!! This is handled by the unique index on (student, elective) in the StudentElectives table and the SERIALIZABLE transaction isolation level!
        val alreadyEnrolled = StudentElectives
            .selectAll()
            .where { (StudentElectives.student eq studentId) and (StudentElectives.elective eq electiveId) }
            .empty().not()

        if (alreadyEnrolled) return CanEnrollStatus.ALREADY_ENROLLED

        // Check if the student is part of the elective's team (if any)
        val electiveTeamId = Elective.getTeamId(electiveId)
        if (electiveTeamId != null && !hasTeam(studentId, electiveTeamId))
            return CanEnrollStatus.NOT_IN_ELECTIVE_TEAM

        // Check if the student is part of the subject's team (if any)
        val subjectTeamId = Subject.getTeamId(subjectId)
        if (subjectTeamId != null && !hasTeam(studentId, subjectTeamId))
            return CanEnrollStatus.NOT_IN_SUBJECT_TEAM

        return CanEnrollStatus.CAN_ENROLL
    }
}