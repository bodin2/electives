package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.Team
import th.ac.bodin2.electives.db.exists
import th.ac.bodin2.electives.db.models.ElectiveSubjects
import th.ac.bodin2.electives.db.models.Subjects
import th.ac.bodin2.electives.db.models.TeacherSubjects
import th.ac.bodin2.electives.proto.api.SubjectTag

class SubjectServiceImpl : SubjectService {
    @Transactional
    override fun create(
        id: Int,
        name: String,
        description: String?,
        code: String?,
        tag: SubjectTag,
        location: String?,
        capacity: Int,
        team: Int?,
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
                if (!Team.exists(team)) throw EntityNotFoundException(ExceptionEntity.TEAM)
                it[this.team] = team
            }
        }

        if (stmt.insertedCount == 0) throw ConflictException(ExceptionEntity.SUBJECT)

        Subject.wrapRow(stmt.resultedValues!!.first())
    }

    @Transactional
    override fun delete(id: Int) {
        transaction {
            val rows = Subjects.deleteWhere { Subjects.id eq id }
            if (rows == 0) {
                throw EntityNotFoundException(ExceptionEntity.SUBJECT)
            }
        }
    }

    @Transactional
    override fun update(id: Int, update: SubjectService.SubjectUpdate) {
        transaction {
            Subject.assertExists(id)

            try {
                Subjects.update({ Subjects.id eq id }) {
                    if (update.setTeam) {
                        if (update.team != null && !Team.exists(update.team)) throw EntityNotFoundException(
                            ExceptionEntity.TEAM
                        )
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

            if (update.teacherIds != null && update.electiveId != null) {
                val teacherIds = update.teacherIds.distinct()
                teacherIds.forEach { Teacher.assertExists(it) }

                TeacherSubjects.deleteWhere {
                    (TeacherSubjects.subject eq id) and (TeacherSubjects.elective eq update.electiveId)
                }

                TeacherSubjects.batchInsert(teacherIds) { teacherId ->
                    this[TeacherSubjects.teacher] = teacherId
                    this[TeacherSubjects.subject] = id
                    this[TeacherSubjects.elective] = update.electiveId
                }
            }
        }
    }

    override fun getAll() = Subject.all().toList()

    override fun getById(subjectId: Int) = Subject.findById(subjectId)

    override fun getElectiveIds(subjectId: Int): List<Int>? {
        if (!Subject.exists(subjectId)) return null

        return ElectiveSubjects
            .selectAll().where { ElectiveSubjects.subject eq subjectId }
            .map { it[ElectiveSubjects.elective].value }
    }
}
