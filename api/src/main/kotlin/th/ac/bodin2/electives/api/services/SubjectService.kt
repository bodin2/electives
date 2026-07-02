package th.ac.bodin2.electives.api.services

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.utils.dbQuery
import th.ac.bodin2.electives.db.Group
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.db.exists
import th.ac.bodin2.electives.db.models.EnrollmentSubjects
import th.ac.bodin2.electives.db.models.Subjects
import th.ac.bodin2.electives.db.models.TeacherSubjects
import th.ac.bodin2.electives.proto.api.SubjectTag

class SubjectService {
    /**
     * Creates a new subject with the given information.
     *
     * @throws EntityNotFoundException if the specified group or any of the specified teachers do not exist.
     * @throws ConflictException if a subject with the same ID already exists.
     */
    @Transactional
    suspend fun create(
        id: Int,
        name: String,
        description: String? = null,
        code: String? = null,
        tag: SubjectTag = SubjectTag.THAI,
        location: String? = null,
        capacity: Int,
        group: Int? = null,
        thumbnailUrl: String? = null,
        imageUrl: String? = null,
    ) = dbQuery {
        val stmt = Subjects.insertIgnore {
            it[this.id] = id
            it[this.name] = name
            it[this.capacity] = capacity
            it[this.tag] = tag.value
            if (description != null) it[this.description] = description
            if (code != null) it[this.code] = code
            if (location != null) it[this.location] = location
            if (thumbnailUrl != null) it[this.thumbnailUrl] = thumbnailUrl
            if (imageUrl != null) it[this.imageUrl] = imageUrl
            if (group != null) {
                if (!Group.exists(group)) throw EntityNotFoundException(ExceptionEntity.GROUP)
                it[this.group] = group
            }
        }

        if (stmt.insertedCount == 0) throw ConflictException(ExceptionEntity.SUBJECT)

        Subject.wrapRow(stmt.resultedValues!!.first())
    }

    /**
     * Deletes a subject by its ID.
     *
     * @throws EntityNotFoundException if the subject does not exist.
     */
    @Transactional
    suspend fun delete(id: Int) {
        dbQuery {
            val rows = Subjects.deleteWhere { Subjects.id eq id }
            if (rows == 0) {
                throw EntityNotFoundException(ExceptionEntity.SUBJECT)
            }
        }
    }

    /**
     * Updates a subject's information.
     *
     * @throws EntityNotFoundException if the subject or group does not exist.
     * @throws NothingToUpdateException if there's nothing to update.
     */
    @Transactional
    suspend fun update(id: Int, update: SubjectUpdate) = dbQuery {
        Subject.assertExists(id)

        val subject = try {
            val rows = Subjects.updateReturning(where = { Subjects.id eq id }) {
                if (update.setGroup) {
                    if (update.group != null && !Group.exists(update.group)) throw EntityNotFoundException(
                        ExceptionEntity.GROUP
                    )
                    it[group] = update.group
                }

                update.name?.let { name -> it[this.name] = name }
                update.tag?.let { tag -> it[this.tag] = tag.value }
                update.capacity?.let { capacity -> it[this.capacity] = capacity }

                if (update.setDescription) it[description] = update.description
                if (update.setCode) it[code] = update.code
                if (update.setLocation) it[location] = update.location
                if (update.setThumbnailUrl) it[thumbnailUrl] = update.thumbnailUrl
                if (update.setImageUrl) it[imageUrl] = update.imageUrl

                if (it.firstDataSet.isEmpty()) throw NothingToUpdateException()
            }
            Subject.wrapRow(rows.first())
        } catch (e: NothingToUpdateException) {
            update.teacherIds ?: throw e
            Subject.findById(id)!!
        }

        if (update.teacherIds != null && update.enrollmentId != null) {
            val teacherIds = update.teacherIds.distinct()
            teacherIds.forEach { Teacher.assertExists(it) }

            TeacherSubjects.deleteWhere {
                (TeacherSubjects.subject eq id) and (TeacherSubjects.enrollment eq update.enrollmentId)
            }

            TeacherSubjects.batchInsert(teacherIds) { teacherId ->
                this[TeacherSubjects.teacher] = teacherId
                this[TeacherSubjects.subject] = id
                this[TeacherSubjects.enrollment] = update.enrollmentId
            }
        }

        subject
    }

    data class SubjectUpdate(
        val name: String? = null,
        val description: String? = null,
        val code: String? = null,
        val tag: SubjectTag? = null,
        val location: String? = null,
        val capacity: Int? = null,
        val group: Int? = null,
        val teacherIds: List<Int>? = null,
        val enrollmentId: Int? = null,
        val thumbnailUrl: String? = null,
        val imageUrl: String? = null,
        val setDescription: Boolean = false,
        val setCode: Boolean = false,
        val setLocation: Boolean = false,
        val setImageUrl: Boolean = false,
        val setThumbnailUrl: Boolean = false,
        val setGroup: Boolean = false,
    )

    fun getAll() = Subject.all().toList()

    fun getById(subjectId: Int) = Subject.findById(subjectId)

    fun getEnrollmentIds(subjectId: Int): List<Int>? {
        if (!Subject.exists(subjectId)) return null

        return EnrollmentSubjects
            .selectAll().where { EnrollmentSubjects.subject eq subjectId }
            .map { it[EnrollmentSubjects.enrollment].value }
    }

    /**
     * Gets all subjects that a teacher teaches, grouped by enrollment ID.
     *
     * @throws EntityNotFoundException if the teacher does not exist.
     */
    fun getTeacherSubjects(teacherId: Int): Map<Int, Subject> {
        Teacher.assertExists(teacherId)
        return (TeacherSubjects innerJoin Subjects)
            .select(Subjects.columns + TeacherSubjects.enrollment)
            .where { TeacherSubjects.teacher eq teacherId }
            .associate { it[TeacherSubjects.enrollment].value to Subject.wrapRow(it) }
    }
}
