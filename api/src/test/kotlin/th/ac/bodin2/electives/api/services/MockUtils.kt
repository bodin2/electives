package th.ac.bodin2.electives.api.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.jetbrains.exposed.v1.dao.DaoEntityID
import org.jetbrains.exposed.v1.jdbc.EmptySizedIterable
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import th.ac.bodin2.electives.api.services.TestServiceConstants.ELECTIVE_TEAM_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.ELECTIVE_WITHOUT_SUBJECTS_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.SUBJECT_ID
import th.ac.bodin2.electives.api.services.TestServiceConstants.SUBJECT_TEAM_ID
import th.ac.bodin2.electives.db.*
import th.ac.bodin2.electives.db.models.Electives
import th.ac.bodin2.electives.db.models.Subjects
import th.ac.bodin2.electives.db.models.Teams
import th.ac.bodin2.electives.db.models.Users

object MockUtils {
    /**
     * Makes sure handleGetElectiveSubjects -> Subject.toProto() can call Elective.require() without issues
     */
    fun mockElectiveRequire() {
        mockkObject(Elective.Companion)
        every { Elective.require(any()) } answers { mockk(relaxed = true) }
    }

    fun unmockElectiveRequire() {
        unmockkObject(Elective.Companion)
    }

    fun mockElective(id: Int): Elective {
        val mock = mockk<Elective>(relaxed = true)
        every { mock.id } returns DaoEntityID(id, Electives)
        every { mock.name } returns id.toString()
        every { mock.subjects } returns
                if (id == ELECTIVE_WITHOUT_SUBJECTS_ID) EmptySizedIterable()
                else SizedCollection(mockSubject(SUBJECT_ID))
        every { mock.team } returns mockTeam(ELECTIVE_TEAM_ID)

        return mock
    }

    fun mockTeam(id: Int): Team {
        val mock = mockk<Team>(relaxed = true)
        every { mock.id } returns DaoEntityID(id, Teams)

        return mock
    }

    fun mockSubject(id: Int): Subject {
        val mock = mockk<Subject>(relaxed = true)
        every { mock.id } returns DaoEntityID(id, Subjects)
        every { mock.teamId } returns DaoEntityID(SUBJECT_TEAM_ID, Teams)

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
        every { mock.id } returns id
        every { mock.subjects } returns listOf(mockSubject(SUBJECT_ID))

        return mock
    }

    fun mockStudent(id: Int): Student {
        val user = mockUser(id)
        val mock = mockk<Student>(relaxed = true)
        every { mock.user } returns user
        every { mock.id } returns id
        every { mock.teams } returns listOf(mockTeam(ELECTIVE_TEAM_ID), mockTeam(SUBJECT_TEAM_ID))

        return mock
    }
}