package th.ac.bodin2.electives.api.services

import th.ac.bodin2.electives.NotFoundException
import th.ac.bodin2.electives.NothingToUpdateException
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.proto.api.SubjectTag

interface SubjectService {
    @CreatesTransaction
    fun create(
        id: Int,
        name: String,
        description: String? = null,
        code: String? = null,
        tag: SubjectTag = SubjectTag.THAI,
        location: String? = null,
        capacity: Int,
        team: Int? = null,
        teacherIds: List<Int> = emptyList(),
        thumbnailUrl: String? = null,
        imageUrl: String? = null,
    ): Subject

    /**
     * Deletes a subject by its ID.
     *
     * @throws NotFoundException if the subject does not exist.
     */
    @CreatesTransaction
    fun delete(id: Int)

    /**
     * Updates a subject's information.
     *
     * @throws NotFoundException if the subject or team does not exist.
     * @throws NothingToUpdateException if there's nothing to update.
     */
    @CreatesTransaction
    fun update(id: Int, update: SubjectUpdate)

    data class SubjectUpdate(
        val name: String? = null,
        val description: String? = null,
        val code: String? = null,
        val tag: SubjectTag? = null,
        val location: String? = null,
        val capacity: Int? = null,
        val team: Int? = null,
        val teacherIds: List<Int>? = null,
        val thumbnailUrl: String? = null,
        val imageUrl: String? = null,
        val patchTeachers: Boolean = false,
    )

    fun getAll(): List<Subject>

    fun getById(subjectId: Int): Subject?
}
