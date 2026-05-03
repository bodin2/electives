package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.ConflictException
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.proto.api.SubjectTag

interface SubjectService {
    /**
     * Creates a new subject with the given information.
     *
     * @throws EntityNotFoundException if the specified team or any of the specified teachers do not exist.
     * @throws ConflictException if a subject with the same ID already exists.
     */
    @Transactional
    fun create(
        id: Int,
        name: String,
        description: String? = null,
        code: String? = null,
        tag: SubjectTag = SubjectTag.THAI,
        location: String? = null,
        capacity: Int,
        team: Int? = null,
        thumbnailUrl: String? = null,
        imageUrl: String? = null,
    ): Subject

    /**
     * Deletes a subject by its ID.
     *
     * @throws EntityNotFoundException if the subject does not exist.
     */
    @Transactional
    fun delete(id: Int)

    /**
     * Updates a subject's information.
     *
     * @throws EntityNotFoundException if the subject or team does not exist.
     * @throws NothingToUpdateException if there's nothing to update.
     */
    @Transactional
    fun update(id: Int, update: SubjectUpdate): Subject

    data class SubjectUpdate(
        val name: String? = null,
        val description: String? = null,
        val code: String? = null,
        val tag: SubjectTag? = null,
        val location: String? = null,
        val capacity: Int? = null,
        val team: Int? = null,
        val teacherIds: List<Int>? = null,
        val electiveId: Int? = null,
        val thumbnailUrl: String? = null,
        val imageUrl: String? = null,
        val setDescription: Boolean = false,
        val setCode: Boolean = false,
        val setLocation: Boolean = false,
        val setImageUrl: Boolean = false,
        val setThumbnailUrl: Boolean = false,
        val setTeam: Boolean = false,
    )

    fun getAll(): List<Subject>

    fun getById(subjectId: Int): Subject?

    fun getElectiveIds(subjectId: Int): List<Int>?

    /**
     * Gets all subjects that a teacher teaches, grouped by elective ID.
     *
     * @throws EntityNotFoundException if the teacher does not exist.
     */
    fun getTeacherSubjects(teacherId: Int): Map<Int, Subject>
}
