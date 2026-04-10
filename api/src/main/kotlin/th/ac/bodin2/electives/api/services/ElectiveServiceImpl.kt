package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.ElectiveService.QueryResult
import th.ac.bodin2.electives.db.*
import th.ac.bodin2.electives.db.models.ElectiveSubjects
import th.ac.bodin2.electives.db.models.Electives
import java.time.LocalDateTime

class ElectiveServiceImpl : ElectiveService {
    @Transactional
    override fun create(
        id: Int,
        name: String,
        team: Int?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ) = transaction {
        val stmt = Electives.insertIgnore {
            it[this.id] = id
            it[this.name] = name
            it[this.startDate] = startDate
            it[this.endDate] = endDate

            if (team != null) {
                if (!Team.exists(team)) throw EntityNotFoundException(ExceptionEntity.TEAM)
                it[this.team] = team
            }
        }

        if (stmt.insertedCount == 0) throw ConflictException(ExceptionEntity.ELECTIVE)
        Elective.wrapRow(stmt.resultedValues!!.first())
    }


    @Transactional
    override fun delete(id: Int) {
        transaction {
            val rows = Electives.deleteWhere { Electives.id eq id }
            if (rows == 0) {
                throw EntityNotFoundException(ExceptionEntity.ELECTIVE)
            }
        }
    }

    @Transactional
    override fun update(id: Int, update: ElectiveService.ElectiveUpdate) {
        transaction {
            Elective.assertExists(id)

            Electives.update({ Electives.id eq id }) {
                update.name?.let { name -> it[this.name] = name }
                if (update.setStartDate) it[this.startDate] = update.startDate
                if (update.setEndDate) it[this.endDate] = update.endDate
                if (update.setTeam) {
                    if (update.team != null && !Team.exists(update.team)) throw EntityNotFoundException(ExceptionEntity.TEAM)
                    it[this.team] = update.team
                }

                if (it.firstDataSet.isEmpty()) throw NothingToUpdateException()
            }
        }
    }

    @Transactional
    override fun setSubjects(electiveId: Int, subjectIds: List<Int>) {
        transaction {
            Elective.assertExists(electiveId)

            val subjectIds = subjectIds.distinct()
            subjectIds.forEach { Subject.assertExists(it) }

            ElectiveSubjects.deleteWhere { ElectiveSubjects.elective eq electiveId }
            ElectiveSubjects.batchInsert(subjectIds) { subjectId ->
                this[ElectiveSubjects.elective] = electiveId
                this[ElectiveSubjects.subject] = subjectId
            }
        }
    }

    override fun getAll() = Elective.all().toList()

    override fun getById(electiveId: Int) = Elective.findById(electiveId)

    override fun getSubjects(electiveId: Int): QueryResult<out List<Subject>> {
        if (!Elective.exists(electiveId)) return QueryResult.ElectiveNotFound
        return QueryResult.Success(Elective.getSubjects(electiveId))
    }

    override fun getSubject(electiveId: Int, subjectId: Int): QueryResult<out Subject> {
        try {
            Elective.assertExists(electiveId)
            Subject.assertExists(subjectId)

            if (!Subject.isPartOfElective(subjectId, electiveId)) {
                return QueryResult.SubjectNotPartOfElective(subjectId, electiveId)
            }

            return QueryResult.Success(Subject.findById(subjectId)!!)
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.SUBJECT -> QueryResult.SubjectNotFound
                ExceptionEntity.ELECTIVE -> QueryResult.ElectiveNotFound
                else -> throw e
            }
        }
    }

    override fun getSubjectMembers(
        electiveId: Int,
        subjectId: Int,
        withStudents: Boolean,
    ): QueryResult<out Pair<List<Teacher>, List<Student>>> {
        try {
            Elective.assertExists(electiveId)
            Subject.assertExists(subjectId)

            if (!Subject.isPartOfElective(subjectId, electiveId)) {
                return QueryResult.SubjectNotPartOfElective(subjectId, electiveId)
            }

            val teachers = Subject.getTeachers(subjectId)
            val students = if (withStudents) Subject.getStudents(subjectId, electiveId) else emptyList()

            return QueryResult.Success(teachers to students)
        } catch (e: EntityNotFoundException) {
            return when (e.entity) {
                ExceptionEntity.SUBJECT -> QueryResult.SubjectNotFound
                ExceptionEntity.ELECTIVE -> QueryResult.ElectiveNotFound
                else -> throw e
            }
        }
    }
}