package th.ac.bodin2.electives.api.services.mock

import th.ac.bodin2.electives.api.MockUtils
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.api.services.SubjectService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.proto.api.SubjectTag

class TestSubjectService : SubjectService {
    companion object {
        val SUBJECT_IDS = listOf(SUBJECT_ID)
    }

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
    ): Subject = error("Not testable")

    @CreatesTransaction
    override fun delete(id: Int) = error("Not testable")

    @CreatesTransaction
    override fun update(id: Int, update: SubjectService.SubjectUpdate) = error("Not testable")

    override fun getAll() = SUBJECT_IDS.map { id -> MockUtils.mockSubject(id) }

    override fun getById(subjectId: Int) =
        if (subjectId in SUBJECT_IDS) MockUtils.mockSubject(subjectId)
        else null
}
