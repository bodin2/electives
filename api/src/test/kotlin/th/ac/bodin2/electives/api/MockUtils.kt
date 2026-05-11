package th.ac.bodin2.electives.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.DaoEntityID
import org.jetbrains.exposed.v1.jdbc.EmptySizedIterable
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_GROUP_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.ENROLLMENT_WITHOUT_SUBJECTS_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_GROUP_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.TEACHER_ID
import th.ac.bodin2.electives.db.*
import th.ac.bodin2.electives.db.models.Enrollments
import th.ac.bodin2.electives.db.models.Groups
import th.ac.bodin2.electives.db.models.Subjects
import th.ac.bodin2.electives.db.models.Users

object MockUtils {
    /**
     * Makes sure handleGetEnrollmentSubjects -> Subject.toProto() can call Enrollment.require() without issues
     */
    fun mockDAOHelpers() {
        mockkObject(Enrollment.Companion)
        every { Enrollment.assertExists(any()) } answers { mockk(relaxed = true) }
    }

    fun unmockDAOHelpers() {
        unmockkObject(Enrollment.Companion)
    }

    fun mockEnrollment(id: Int): Enrollment {
        val mock = mockk<Enrollment>(relaxed = true)
        every { mock.id } returns DaoEntityID(id, Enrollments)
        every { mock.name } returns id.toString()
        every { mock.subjects } returns
                if (id == ENROLLMENT_WITHOUT_SUBJECTS_ID) EmptySizedIterable()
                else SizedCollection(mockSubject(SUBJECT_ID))
        every { mock.groupId } returns EntityID(ENROLLMENT_GROUP_ID, Groups)

        return mock
    }

    fun mockGroup(id: Int): Group {
        val mock = mockk<Group>(relaxed = true)
        every { mock.id } returns DaoEntityID(id, Groups)

        return mock
    }

    fun mockSubject(id: Int): Subject {
        val mock = mockk<Subject>(relaxed = true)
        every { mock.id } returns DaoEntityID(id, Subjects)
        every { mock.groupId } returns DaoEntityID(SUBJECT_GROUP_ID, Groups)
        every { mock.getTeachers(any()) } returns listOf(mockTeacher(TEACHER_ID))
        every { mock.getEnrolledCount(any()) } returns 0

        return mock
    }

    private fun mockUser(id: Int): User {
        val mock = mockk<User>(relaxed = true)
        every { mock.id } returns DaoEntityID(id, Users)

        return mock
    }

    fun mockTeacher(id: Int): Teacher {
        val user = mockUser(id)
        val mock = mockk<Teacher>(relaxed = true)
        every { mock.user } returns user
        every { mock.id } returns mockId(id)

        return mock
    }

    fun mockAdmin(id: Int): Admin {
        val user = mockUser(id)
        val mock = mockk<Admin>(relaxed = true)
        every { mock.user } returns user
        every { mock.id } returns mockId(id)

        return mock
    }

    fun mockStudent(id: Int): Student {
        val user = mockUser(id)
        val mock = mockk<Student>(relaxed = true)
        every { mock.user } returns user
        every { mock.id } returns mockId(id)
        every { mock.groups } returns SizedCollection(
            listOf(
                mockGroup(ENROLLMENT_GROUP_ID),
                mockGroup(SUBJECT_GROUP_ID),
            )
        )

        return mock
    }

    fun mockId(id: Int): EntityID<Int> {
        val entId = mockk<EntityID<Int>>()
        every { entId.value } returns id
        return entId
    }
}
