package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
import th.ac.bodin2.electives.db.models.Subjects
import th.ac.bodin2.electives.proto.api.UserType
import java.sql.Connection
import java.sql.Connection.TRANSACTION_SERIALIZABLE
import java.time.LocalDateTime

class ElectiveSelectionServiceImpl(
    private val usersService: UsersService,
    private val notificationsService: NotificationsService
) : ElectiveSelectionService {
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

    @CreatesTransaction
    override fun forceSetAllStudentSelections(userId: Int, selections: Map<Int, Int>) {
        transaction {
            StudentElectives.deleteWhere { StudentElectives.student eq userId }
            StudentElectives.batchInsert(selections.toList()) { (electiveId, subjectId) ->
                Elective.require(electiveId)
                Subject.require(subjectId)

                this[StudentElectives.student] = userId
                this[StudentElectives.elective] = electiveId
                this[StudentElectives.subject] = subjectId
            }
        }
    }

    // We're currently using SQLite, which locks during writes, so technically all of this code to prevent TOCTOU doesn't really matter.
    // But in case if we ever switch databases, this will be useful.

    // We want to prevent overbooking: Thread A reads (29/30) -> Thread B reads (29/30) -> A inserts (30/30) -> B inserts (31/30)
    // SERIALIZABLE will ensure that if two transactions try to do this at the same time, one of them will be fail.
    @CreatesTransaction
    override fun setStudentSelection(
        executorId: Int,
        userId: Int,
        electiveId: Int,
        subjectId: Int
    ): ModifySelectionResult {
        var onSuccess: (() -> Unit)? = null

        val result = transaction(transactionIsolation = TRANSACTION_SERIALIZABLE) {
            maxAttempts = 3

            try {
                val student = Student.require(userId)
                val elective = Elective.require(electiveId)
                val subject = Subject.require(subjectId)

                val executorIsSomeoneElse = student.id != executorId
                if (executorIsSomeoneElse) {
                    if (usersService.getUserType(executorId) == UserType.TEACHER) {
                        val teacher = Teacher.require(executorId)
                        if (!Teacher.teachesSubject(teacher, subjectId)) {
                            return@transaction ModifySelectionResult.CannotModify(ModifySelectionStatus.FORBIDDEN)
                        }
                    } else {
                        return@transaction ModifySelectionResult.CannotModify(ModifySelectionStatus.FORBIDDEN)
                    }
                }

                // By the time we reach here, bypassDateCheck will only be true if the executor is a teacher
                when (val result = canEnrollInSubject(student, elective, subject, executorIsSomeoneElse)) {
                    CanEnrollStatus.CAN_ENROLL -> {}

                    else -> return@transaction ModifySelectionResult.CannotEnroll(result)
                }

                (connection.connection as Connection).prepareStatement(INSERT_SELECTION_QUERY).use { ps ->
                    ps.setInt(1, userId)
                    ps.setInt(2, electiveId)
                    ps.setInt(3, subjectId)

                    ps.setInt(4, electiveId)
                    ps.setInt(5, subjectId)

                    ps.setInt(6, subjectId)

                    val inserted = ps.executeUpdate()
                    if (inserted == 0) {
                        logger.debug("Student elective selection failed due to full subject, user: $userId, elective: $electiveId, subject: $subjectId, executor: $executorId")

                        // Not inserted, subject is full
                        return@transaction ModifySelectionResult.CannotEnroll(
                            CanEnrollStatus.SUBJECT_FULL
                        )
                    }
                }

                onSuccess = {
                    logger.debug("Student elective selected, user: $userId, elective: $electiveId, subject: $subjectId, executor: $executorId")

                    notificationsService.notifySubjectSelectionUpdate(
                        electiveId,
                        subjectId,
                        transaction { Subject.getEnrolledCount(subject, elective) }
                    )
                }

                return@transaction ModifySelectionResult.Success
            } catch (e: NotFoundException) {
                return@transaction e.tryHandling()
            }
        }

        onSuccess?.invoke()

        return result
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

                logger.debug("Student elective removed, user: $userId, elective: $electiveId, executor: $executorId")

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

    override fun getStudentSelections(userId: Int): Map<Int, Subject> {
        val student = Student.require(userId)
        return buildMap { Student.getAllElectiveSelections(student).forEach { put(it.first, it.second) } }
    }

    /**
     * Checks if the student can enroll in the specified subject of the elective by:
     *
     * 1. Checking if the elective has started/ended.
     * 2. Checking if the subject is part of the elective (nice try).
     * 3. Checking if the student is already enrolled in another subject of the same elective.
     * 4. Checking if the student is part of the elective's team (if any).
     * 5. Checking if the student is part of the subject's team (if any).
     * ~~6. Checking if the subject is full.~~
     */
    private fun canEnrollInSubject(
        student: Reference,
        elective: Elective.Reference,
        subject: Subject.Reference,
        bypassDateCheck: Boolean,
    ): CanEnrollStatus {
        // Check elective date range
        val (startDate, endDate) = Elective.getEnrollmentDateRange(elective)
        val now = LocalDateTime.now()
        if (!bypassDateCheck) {
            if (startDate != null && now.isBefore(startDate)) return CanEnrollStatus.NOT_IN_ELECTIVE_DATE_RANGE
            if (endDate != null && now.isAfter(endDate)) return CanEnrollStatus.NOT_IN_ELECTIVE_DATE_RANGE
        }

        if (!Subject.isPartOfElective(subject, elective)) return CanEnrollStatus.SUBJECT_NOT_IN_ELECTIVE

        // Check if the student is already enrolled in another subject of the same elective
        // UNSAFE FROM TOCTOU by its own!!! This is handled by the unique index on (student, elective) in the StudentElectives table and the SERIALIZABLE transaction isolation level!
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

//        if (Subject.isFull(subject, elective))
//            return CanEnrollStatus.SUBJECT_FULL

        return CanEnrollStatus.CAN_ENROLL
    }
}