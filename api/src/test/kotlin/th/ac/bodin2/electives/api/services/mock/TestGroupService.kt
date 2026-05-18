package th.ac.bodin2.electives.api.services.mock

import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.MockUtils
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.GroupService
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_GROUP_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_GROUP_ID
import th.ac.bodin2.electives.db.Group
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher
import th.ac.bodin2.electives.proto.api.GroupType

class TestGroupService : GroupService {
    companion object {
        val GROUP_IDS = listOf(ENROLLMENT_GROUP_ID, SUBJECT_GROUP_ID)
    }

    @Transactional
    override fun create(id: Int, name: String, type: GroupType): Group = error("Not testable")

    @Transactional
    override fun delete(id: Int) = error("Not testable")

    @Transactional
    override fun update(id: Int, update: GroupService.GroupUpdate): Group {
        if (id !in GROUP_IDS) throw EntityNotFoundException(ExceptionEntity.GROUP)
        return MockUtils.mockGroup(id)
    }

    override fun getAll() = GROUP_IDS.map { id -> MockUtils.mockGroup(id) }

    override fun getById(groupId: Int) =
        if (groupId in GROUP_IDS) MockUtils.mockGroup(groupId)
        else null

    @Transactional
    override fun getMembers(groupId: Int, page: Int, query: String?): Pair<List<Student>, Long> {
        if (groupId !in GROUP_IDS) throw EntityNotFoundException(ExceptionEntity.GROUP)
        return emptyList<Student>() to 0L
    }

    @Transactional
    override fun getManagers(groupId: Int, page: Int, query: String?): Pair<List<Teacher>, Long> {
        if (groupId !in GROUP_IDS) throw EntityNotFoundException(ExceptionEntity.GROUP)
        return emptyList<Teacher>() to 0L
    }

    override fun getMemberCounts() = GROUP_IDS.associateWith { 0 }

    override fun getMemberCount(groupId: Int): Int = 0

    @Transactional
    override fun deleteMembers(groupId: Int) = error("Not testable")

    @Transactional
    override fun migrateMembers(groupId: Int, targetGroupId: Int) = error("Not testable")
}
