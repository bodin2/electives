package th.ac.bodin2.electives.api.services.mock

import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.MockUtils
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.SubjectService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ELECTIVE_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.TEACHER_ID
import th.ac.bodin2.electives.db.Subject
import th.ac.bodin2.electives.proto.api.SubjectTag

class TestSubjectService : SubjectService {
    companion object {
        val SUBJECT_IDS = listOf(SUBJECT_ID)
    }

    @Transactional
    override fun create(
        id: UInt,
        name: String,
        description: String?,
        code: String?,
        tag: SubjectTag,
        location: String?,
        capacity: Int,
        team: UInt?,
        thumbnailUrl: String?,
        imageUrl: String?,
    ): Subject = error("Not testable")

    @Transactional
    override fun delete(id: UInt) = error("Not testable")

    @Transactional
    override fun update(id: UInt, update: SubjectService.SubjectUpdate): Subject {
        if (id !in SUBJECT_IDS) throw EntityNotFoundException(ExceptionEntity.SUBJECT)
        return MockUtils.mockSubject(id)
    }

    override fun getAll() = SUBJECT_IDS.map { id -> MockUtils.mockSubject(id) }

    override fun getById(subjectId: UInt) =
        if (subjectId in SUBJECT_IDS) MockUtils.mockSubject(subjectId)
        else null

    override fun getElectiveIds(subjectId: UInt): List<UInt> = listOf(ELECTIVE_ID)

    override fun getTeacherSubjects(teacherId: UInt): Map<UInt, Subject> {
        if (teacherId != TEACHER_ID) throw EntityNotFoundException(ExceptionEntity.TEACHER)
        return mapOf(ELECTIVE_ID to MockUtils.mockSubject(SUBJECT_ID))
    }
}
