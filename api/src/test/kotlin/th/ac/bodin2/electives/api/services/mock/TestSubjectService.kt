package th.ac.bodin2.electives.api.services.mock

import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.MockUtils
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.SubjectService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ELECTIVE_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.proto.api.SubjectTag

class TestSubjectService : SubjectService {
    companion object {
        val SUBJECT_IDS = listOf(SUBJECT_ID)
    }

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
    ): Subject = error("Not testable")

    @Transactional
    override fun delete(id: Int) = error("Not testable")

    @Transactional
    override fun update(id: Int, update: SubjectService.SubjectUpdate): Subject {
        if (id !in SUBJECT_IDS) throw EntityNotFoundException(ExceptionEntity.SUBJECT)
        return MockUtils.mockSubject(id)
    }

    override fun getAll() = SUBJECT_IDS.map { id -> MockUtils.mockSubject(id) }

    override fun getById(subjectId: Int) =
        if (subjectId in SUBJECT_IDS) MockUtils.mockSubject(subjectId)
        else null

    override fun getElectiveIds(subjectId: Int): List<Int> = listOf(ELECTIVE_ID)
}
