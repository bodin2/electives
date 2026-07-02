package th.ac.bodin2.electives.api.services.mock

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.MockUtils
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.GroupService
import th.ac.bodin2.electives.api.services.GroupService.GroupUpdate
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_GROUP_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_GROUP_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.TEACHER_ID
import th.ac.bodin2.electives.db.Student
import th.ac.bodin2.electives.db.Teacher

object TestGroupService {
    val GROUP_IDS = listOf(ENROLLMENT_GROUP_ID, SUBJECT_GROUP_ID)

    @OptIn(Transactional::class)
    operator fun invoke(): GroupService = mockk(relaxed = true) {
        coEvery { update(any(), any()) } answers {
            val id = firstArg<Int>()
            val update = secondArg<GroupUpdate>()
            if (id !in GROUP_IDS) throw EntityNotFoundException(ExceptionEntity.GROUP)
            MockUtils.mockGroup(id, parentId = if (update.setParentId) update.parentId else null)
        }

        every { getAll() } answers { GROUP_IDS.map { id -> MockUtils.mockGroup(id) } }

        every { getById(any()) } answers {
            val groupId = firstArg<Int>()
            if (groupId in GROUP_IDS) MockUtils.mockGroup(groupId) else null
        }

        coEvery { getMembers(any(), any(), any()) } answers {
            val groupId = firstArg<Int>()
            if (groupId !in GROUP_IDS) throw EntityNotFoundException(ExceptionEntity.GROUP)
            emptyList<Student>() to 0L
        }

        coEvery { getManagers(any(), any(), any()) } answers {
            val groupId = firstArg<Int>()
            if (groupId !in GROUP_IDS) throw EntityNotFoundException(ExceptionEntity.GROUP)
            emptyList<Teacher>() to 0L
        }

        coEvery { getTeacherGroups(any()) } answers {
            val teacherId = firstArg<Int>()
            if (teacherId != TEACHER_ID) {
                throw EntityNotFoundException(ExceptionEntity.TEACHER)
            }
            GROUP_IDS.map { MockUtils.mockGroup(it) }
        }

        every { getMemberCounts() } answers { GROUP_IDS.associateWith { 0 } }

        every { getMemberCount(any()) } returns 0
    }
}
