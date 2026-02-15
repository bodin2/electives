package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.Team
import th.ac.bodin2.electives.db.exists
import th.ac.bodin2.electives.db.models.Subjects
import th.ac.bodin2.electives.db.models.TeacherSubjects
import th.ac.bodin2.electives.proto.api.SubjectTag

class SubjectServiceImpl : SubjectService {
    @CreatesTransaction
    override fun create(
        id: Int,
        name: String,
        description: String?,
        code: String?,
        tag: SubjectTag,
        location: String?,
        capacity: Int,
        team: Int?,
        teacherIds: List<Int>,
        thumbnailUrl: String?,
        imageUrl: String?,
    ) = transaction {
        val stmt = Subjects.insertIgnore {
            it[this.id] = id
            it[this.name] = name
            it[this.capacity] = capacity
            it[this.tag] = tag.number
            if (description != null) it[this.description] = description
            if (code != null) it[this.code] = code
            if (location != null) it[this.location] = location
            if (thumbnailUrl != null) it[this.thumbnailUrl] = thumbnailUrl
            if (imageUrl != null) it[this.imageUrl] = imageUrl
            if (team != null) {
                if (!Team.exists(team)) throw NotFoundException(ExceptionEntity.TEAM)
                it[this.team] = team
            }
        }

        if (stmt.insertedCount == 0) throw ConflictException(ExceptionEntity.SUBJECT)

        if (teacherIds.isNotEmpty()) {
            TeacherSubjects.batchInsert(teacherIds) { teacherId ->
                Teacher.require(teacherId)

                this[TeacherSubjects.teacher] = teacherId
                this[TeacherSubjects.subject] = id
            }
        }

        Subject.wrapRow(stmt.resultedValues!!.first())
    }

    @CreatesTransaction
    override fun delete(id: Int) {
        transaction {
            val rows = Subjects.deleteWhere { Subjects.id eq id }
            if (rows == 0) {
                throw NotFoundException(ExceptionEntity.SUBJECT)
            }
        }
    }

    @CreatesTransaction
    override fun update(id: Int, update: SubjectService.SubjectUpdate) {
        transaction {
            Subject.require(id)

            try {
                Subjects.update({ Subjects.id eq id }) {
                    if (update.setTeam) {
                        if (update.team != null && !Team.exists(update.team)) throw NotFoundException(ExceptionEntity.TEAM)
                        it[team] = update.team
                    }

                    update.name?.let { name -> it[this.name] = name }
                    update.tag?.let { tag -> it[this.tag] = tag.number }
                    update.capacity?.let { capacity -> it[this.capacity] = capacity }

                    if (update.setDescription) it[description] = update.description
                    if (update.setCode) it[code] = update.code
                    if (update.setLocation) it[location] = update.location
                    if (update.setThumbnailUrl) it[thumbnailUrl] = update.thumbnailUrl
                    if (update.setImageUrl) it[imageUrl] = update.imageUrl

                    if (it.firstDataSet.isEmpty()) throw NothingToUpdateException()
                }
            } catch (e: NothingToUpdateException) {
                update.teacherIds ?: throw e
            }

            if (update.teacherIds != null) {
                TeacherSubjects.deleteWhere { TeacherSubjects.subject eq id }

                TeacherSubjects.batchInsert(update.teacherIds) { teacherId ->
                    Teacher.require(teacherId)

                    this[TeacherSubjects.teacher] = teacherId
                    this[TeacherSubjects.subject] = id
                }
            }
        }
    }

    override fun getAll() = Subject.all().toList()

    override fun getById(subjectId: Int) = Subject.findById(subjectId)
}
