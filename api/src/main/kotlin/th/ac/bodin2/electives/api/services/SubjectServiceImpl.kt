package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.Teacher
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
        val subject = Subject.wrapRow(Subjects.insert {
            it[this.id] = id
            it[this.name] = name
            it[this.description] = description
            it[this.code] = code
            it[this.tag] = tag.number
            it[this.location] = location
            it[this.capacity] = capacity
            it[this.team] = team
            it[this.thumbnailUrl] = thumbnailUrl
            it[this.imageUrl] = imageUrl
        }.resultedValues!!.first())

        if (teacherIds.isNotEmpty()) {
            TeacherSubjects.batchInsert(teacherIds) { teacherId ->
                Teacher.require(teacherId)

                this[TeacherSubjects.teacher] = teacherId
                this[TeacherSubjects.subject] = id
            }
        }

        subject
    }

    @CreatesTransaction
    override fun delete(id: Int) {
        transaction {
            val rows = Subjects.deleteWhere { Subjects.id eq id }
            if (rows == 0) {
                throw NotFoundException(NotFoundEntity.SUBJECT)
            }
        }
    }

    @CreatesTransaction
    override fun update(id: Int, update: SubjectService.SubjectUpdate) {
        transaction {
            Subject.require(id)
            Subjects.update({ Subjects.id eq id }) {
                update.name?.let { name -> it[this.name] = name }
                update.description?.let { description -> it[this.description] = description }
                update.code?.let { code -> it[this.code] = code }
                update.tag?.let { tag -> it[this.tag] = tag.number }
                update.location?.let { location -> it[this.location] = location }
                update.capacity?.let { capacity -> it[this.capacity] = capacity }
                update.team?.let { team -> it[this.team] = team }
                update.thumbnailUrl?.let { thumbnailUrl -> it[this.thumbnailUrl] = thumbnailUrl }
                update.imageUrl?.let { imageUrl -> it[this.imageUrl] = imageUrl }

                if (it.firstDataSet.isEmpty()) throw NothingToUpdateException()
            }

            if (update.patchTeachers && update.teacherIds != null) {
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
