package th.ac.bodin2.electives.api

import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import th.ac.bodin2.electives.NotFoundEntity
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.api.services.ElectiveSelectionService
import th.ac.bodin2.electives.api.services.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.db.models.ElectiveSubjects
import th.ac.bodin2.electives.db.models.Electives
import th.ac.bodin2.electives.db.models.Subjects
import th.ac.bodin2.electives.proto.api.SubjectTag
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(CreatesTransaction::class)
class ElectiveSelectionServiceImplTest : ApplicationTest() {
    private val ApplicationTestBuilder.electiveSelectionService: ElectiveSelectionService
        get() {
            val service: ElectiveSelectionService by application.dependencies
            return service
        }

    @Test
    fun `put student elective selection`() = runTest {
        electiveSelectionService.setStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        )

        electiveSelectionService.getStudentSelections(TestConstants.Students.JOHN_ID).apply {
            val selectedSubject = this[TestConstants.Electives.SCIENCE_ID]
            assertEquals(TestConstants.Subjects.PHYSICS_ID, selectedSubject?.id?.value)
        }
    }

    @Test
    fun `put student elective selection subject not in elective`() = runTest {
        transaction {
            Subjects.insert {
                it[id] = TestConstants.Subjects.OTHER_ID
                it[name] = "Other Subject"
                it[code] = "OTH999"
                it[tag] = SubjectTag.MATH.number
                it[location] = "Room B"
                it[capacity] = 10
                it[team] = null
            }
        }

        val result = electiveSelectionService.setStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID,
            TestConstants.Subjects.OTHER_ID
        )

        assertIs<ElectiveSelectionService.ModifySelectionResult.CannotEnroll>(result)
        assertEquals(ElectiveSelectionService.CanEnrollStatus.SUBJECT_NOT_IN_ELECTIVE, result.status)
    }

    @Test
    fun `put student elective selection as teacher`() = runTest {
        val result = electiveSelectionService.setStudentSelection(
            TestConstants.Teachers.BOB_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        )

        assertIs<ElectiveSelectionService.ModifySelectionResult.Success>(result)
    }

    @Test
    fun `put student elective selection as teacher not teaching subject`() = runTest {
        val result = electiveSelectionService.setStudentSelection(
            TestConstants.Teachers.ALICE_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        )

        assertIs<ElectiveSelectionService.ModifySelectionResult.CannotModify>(result)
        assertEquals(ElectiveSelectionService.ModifySelectionStatus.FORBIDDEN, result.status)
    }

    @Test
    fun `delete student elective selection`() = runTest {
        electiveSelectionService.setStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        ).apply {
            assertIs<ElectiveSelectionService.ModifySelectionResult.Success>(this, "Failed to set up selection")
        }

        val result = electiveSelectionService.deleteStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID
        )

        assertIs<ElectiveSelectionService.ModifySelectionResult.Success>(result)

        val selections = electiveSelectionService.getStudentSelections(TestConstants.Students.JOHN_ID)
        assertTrue(selections.isEmpty())
    }

    @Test
    fun `delete non existent selection`() = runTest {
        val result = electiveSelectionService.deleteStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID
        )

        assertIs<ElectiveSelectionService.ModifySelectionResult.CannotModify>(result)
        assertEquals(
            ElectiveSelectionService.ModifySelectionStatus.NOT_ENROLLED,
            result.status
        )
    }

    @Test
    fun `put student elective selection with invalid elective id`() = runTest {
        val result = electiveSelectionService.setStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            UNUSED_ID,
            TestConstants.Subjects.PHYSICS_ID
        )

        assertIs<ElectiveSelectionService.ModifySelectionResult.NotFound>(result)
        assertEquals(NotFoundEntity.ELECTIVE, result.entity)
    }

    @Test
    fun `put student elective selection with invalid subject id`() = runTest {
        val result = electiveSelectionService.setStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID,
            UNUSED_ID,
        )

        assertIs<ElectiveSelectionService.ModifySelectionResult.NotFound>(result)
        assertEquals(NotFoundEntity.SUBJECT, result.entity)
    }

    @Test
    fun `put student elective selection as other student`() = runTest {
        val result = electiveSelectionService.setStudentSelection(
            TestConstants.Students.JANE_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        )

        assertIs<ElectiveSelectionService.ModifySelectionResult.CannotModify>(result)
        assertEquals(ElectiveSelectionService.ModifySelectionStatus.FORBIDDEN, result.status)
    }

    @Test
    fun `delete student elective selection as other student`() = runTest {
        electiveSelectionService.setStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        ).apply {
            assertIs<ElectiveSelectionService.ModifySelectionResult.Success>(this, "Failed to set up selection")
        }

        val result = electiveSelectionService.deleteStudentSelection(
            TestConstants.Students.JANE_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID
        )

        assertIs<ElectiveSelectionService.ModifySelectionResult.CannotModify>(result)
        assertEquals(ElectiveSelectionService.ModifySelectionStatus.FORBIDDEN, result.status)
    }

    @Test
    fun `delete student elective selection as teacher`() = runTest {
        electiveSelectionService.setStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        ).apply {
            assertIs<ElectiveSelectionService.ModifySelectionResult.Success>(this, "Failed to set up selection")
        }

        val result = electiveSelectionService.deleteStudentSelection(
            TestConstants.Teachers.BOB_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID
        )

        assertIs<ElectiveSelectionService.ModifySelectionResult.Success>(result)

        val selections = electiveSelectionService.getStudentSelections(TestConstants.Students.JOHN_ID)
        assertTrue(selections.isEmpty())
    }

    @Test
    fun `put student elective selection update existing`() = runTest {
        electiveSelectionService.setStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        ).apply {
            assertIs<ElectiveSelectionService.ModifySelectionResult.Success>(this, "Failed to set up selection")
        }

        val result = electiveSelectionService.setStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID,
            TestConstants.Subjects.CHEMISTRY_ID
        )

        assertIs<ElectiveSelectionService.ModifySelectionResult.CannotEnroll>(result)
        assertEquals(ElectiveSelectionService.CanEnrollStatus.ALREADY_ENROLLED, result.status)
    }

    @Test
    fun `delete selection with invalid elective id`() = runTest {
        val result = electiveSelectionService.deleteStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            UNUSED_ID,
        )

        assertIs<ElectiveSelectionService.ModifySelectionResult.NotFound>(result)
        assertEquals(NotFoundEntity.ELECTIVE, result.entity)
    }

    @Test
    fun `put student elective selection not in subject team`() = runTest {
        transaction {
            Subjects.insert {
                it[id] = TestConstants.Subjects.OTHER_ID
                it[name] = "Team Subject"
                it[code] = "TMS101"
                it[tag] = SubjectTag.MATH.number
                it[location] = "Room C"
                it[capacity] = 10
                it[team] = TestConstants.Teams.TEAM_2_ID
            }

            ElectiveSubjects.insert {
                it[elective] = TestConstants.Electives.SCIENCE_ID
                it[subject] = TestConstants.Subjects.OTHER_ID
            }
        }

        val result = electiveSelectionService.setStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.SCIENCE_ID,
            TestConstants.Subjects.OTHER_ID,
        )

        assertIs<ElectiveSelectionService.ModifySelectionResult.CannotEnroll>(result)
        assertEquals(ElectiveSelectionService.CanEnrollStatus.NOT_IN_SUBJECT_TEAM, result.status)
    }

    @Test
    fun `put student elective selection not in elective team`() = runTest {
        transaction {
            Subjects.insert {
                it[id] = TestConstants.Subjects.OTHER_ID
                it[name] = "Team Subject"
                it[code] = "TMS101"
                it[tag] = SubjectTag.MATH.number
                it[location] = "Room C"
                it[capacity] = 10
                it[team] = null
            }

            ElectiveSubjects.insert {
                it[elective] = TestConstants.Electives.SCIENCE_ID
                it[subject] = TestConstants.Subjects.OTHER_ID
            }
        }

        // JANE is in TEAM_2, SCIENCE elective requires TEAM_1
        val result = electiveSelectionService.setStudentSelection(
            TestConstants.Students.JANE_ID,
            TestConstants.Students.JANE_ID,
            TestConstants.Electives.SCIENCE_ID,
            TestConstants.Subjects.OTHER_ID,
        )

        assertIs<ElectiveSelectionService.ModifySelectionResult.CannotEnroll>(result)
        assertEquals(ElectiveSelectionService.CanEnrollStatus.NOT_IN_ELECTIVE_TEAM, result.status)
    }

    @Test
    fun `put student elective selection in date ranges`() = runTest {
        transaction {
            Electives.insert {
                it[id] = TestConstants.Electives.OUT_OF_DATE_ID
                it[name] = TestConstants.Electives.OUT_OF_DATE_NAME
                it[startDate] = LocalDateTime.now().minusSeconds(5)
                it[endDate] = LocalDateTime.now().minusSeconds(1)
            }

            ElectiveSubjects.insert {
                it[elective] = TestConstants.Electives.OUT_OF_DATE_ID
                it[subject] = TestConstants.Subjects.PHYSICS_ID
            }
        }

        val result1 = electiveSelectionService.setStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.OUT_OF_DATE_ID,
            TestConstants.Subjects.PHYSICS_ID,
        )

        transaction {
            Electives.update({ Electives.id eq TestConstants.Electives.OUT_OF_DATE_ID }) {
                it[startDate] = LocalDateTime.now().plusSeconds(5)
                it[endDate] = LocalDateTime.now().plusSeconds(10)
            }
        }

        val result2 = electiveSelectionService.setStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.OUT_OF_DATE_ID,
            TestConstants.Subjects.PHYSICS_ID,
        )

        listOf(result1, result2).forEach { result ->
            assertIs<ElectiveSelectionService.ModifySelectionResult.CannotEnroll>(result)
            assertEquals(ElectiveSelectionService.CanEnrollStatus.NOT_IN_ELECTIVE_DATE_RANGE, result.status)
        }

        transaction {
            Electives.update({ Electives.id eq TestConstants.Electives.OUT_OF_DATE_ID }) {
                it[startDate] = LocalDateTime.now()
                it[endDate] = LocalDateTime.now().plusSeconds(10)
            }
        }

        val result3 = electiveSelectionService.setStudentSelection(
            TestConstants.Students.JOHN_ID,
            TestConstants.Students.JOHN_ID,
            TestConstants.Electives.OUT_OF_DATE_ID,
            TestConstants.Subjects.PHYSICS_ID,
        )

        assertIs<ElectiveSelectionService.ModifySelectionResult.Success>(result3)
    }
}