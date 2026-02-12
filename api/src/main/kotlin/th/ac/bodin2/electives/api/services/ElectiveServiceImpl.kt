package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.load
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.api.services.ElectiveService.QueryResult
import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.models.ElectiveSubjects
import th.ac.bodin2.electives.db.models.Electives
import java.time.LocalDateTime

class ElectiveServiceImpl : ElectiveService {
    @CreatesTransaction
    override fun create(
        id: Int,
        name: String,
        team: Int?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ) = transaction {
        Elective.wrapRow(Electives.insert {
            it[this.id] = id
            it[this.name] = name
            it[this.team] = team
            it[this.startDate] = startDate
            it[this.endDate] = endDate
        }.resultedValues!!.first())
    }


    @CreatesTransaction
    override fun delete(id: Int) {
        transaction {
            val rows = Electives.deleteWhere { Electives.id eq id }
            if (rows == 0) {
                throw NotFoundException(NotFoundEntity.ELECTIVE)
            }
        }
    }

    @CreatesTransaction
    override fun update(id: Int, update: ElectiveService.ElectiveUpdate) {
        transaction {
            Elective.require(id)
            Electives.update({ Electives.id eq id }) {
                update.name?.let { name -> it[this.name] = name }
                if (update.setTeam) it[this.team] = update.team
                if (update.setStartDate) it[this.startDate] = update.startDate
                if (update.setEndDate) it[this.endDate] = update.endDate
            }
        }
    }

    @CreatesTransaction
    override fun setSubjects(electiveId: Int, subjectIds: List<Int>) {
        transaction {
            Elective.require(electiveId)

            ElectiveSubjects.deleteWhere { ElectiveSubjects.elective eq electiveId }

            ElectiveSubjects.batchInsert(subjectIds) { subjectId ->
                Subject.require(subjectId)

                this[ElectiveSubjects.elective] = electiveId
                this[ElectiveSubjects.subject] = subjectId
            }
        }
    }

    override fun getAll() = Elective.all().toList()

    override fun getById(electiveId: Int) = Elective.findById(electiveId)?.load(Elective::team)

    override fun getSubjects(electiveId: Int): QueryResult<out List<Subject>> {
        try {
            val elective = Elective.require(electiveId)
            return QueryResult.Success(Elective.getSubjects(elective))
        } catch (e: NotFoundException) {
            return when (e.entity) {
                NotFoundEntity.ELECTIVE -> QueryResult.ElectiveNotFound
                else -> throw e
            }
        }
    }

    override fun getSubject(electiveId: Int, subjectId: Int): QueryResult<out Subject> {
        try {
            val elective = Elective.require(electiveId)
            val subject = Subject.require(subjectId)

            if (!Subject.isPartOfElective(subject, elective)) {
                return QueryResult.SubjectNotPartOfElective(subject.id, elective.id)
            }

            return QueryResult.Success(Subject.findById(subject.id)!!)
        } catch (e: NotFoundException) {
            return when (e.entity) {
                NotFoundEntity.SUBJECT -> QueryResult.SubjectNotFound
                NotFoundEntity.ELECTIVE -> QueryResult.ElectiveNotFound
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
            val elective = Elective.require(electiveId)
            val subject = Subject.require(subjectId)

            if (!Subject.isPartOfElective(subject, elective)) {
                return QueryResult.SubjectNotPartOfElective(subject.id, elective.id)
            }

            val teachers = Subject.getTeachers(subject)
            val students = if (withStudents) Subject.getStudents(subject, elective) else emptyList()

            return QueryResult.Success(teachers to students)
        } catch (e: NotFoundException) {
            return when (e.entity) {
                NotFoundEntity.SUBJECT -> QueryResult.SubjectNotFound
                NotFoundEntity.ELECTIVE -> QueryResult.ElectiveNotFound
                else -> throw e
            }
        }
    }
}