package th.ac.bodin2.electives.api.services

import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import th.ac.bodin2.electives.EntityNotFoundException
import th.ac.bodin2.electives.ExceptionEntity
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.SessionUserMocks.aliceSessionUser
import th.ac.bodin2.electives.api.SessionUserMocks.bobSessionUser
import th.ac.bodin2.electives.api.SessionUserMocks.charlieAdminSessionUser
import th.ac.bodin2.electives.api.SessionUserMocks.janeSessionUser
import th.ac.bodin2.electives.api.SessionUserMocks.johnSessionUser
import th.ac.bodin2.electives.api.TestConstants
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.db.models.EnrollmentSubjects
import th.ac.bodin2.electives.db.models.Enrollments
import th.ac.bodin2.electives.db.models.Subjects
import th.ac.bodin2.electives.db.models.TeacherSubjects
import th.ac.bodin2.electives.db.toProto
import th.ac.bodin2.electives.proto.api.SubjectTag
import java.time.LocalDateTime
import kotlin.test.*

@OptIn(Transactional::class)
class EnrollmentSelectionServiceImplTest : ApplicationTest() {
    private val ApplicationTestBuilder.enrollmentSelectionService: EnrollmentSelectionService
        get() {
            val service: EnrollmentSelectionService by application.dependencies
            return service
        }

    @Test
    fun `put student enrollment selection`() = runTest {
        enrollmentSelectionService.setStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        )

        val selectedSubject = transaction {
            enrollmentSelectionService.getStudentSelections(TestConstants.Students.JOHN_ID)[TestConstants.Enrollments.SCIENCE_ID]?.toProto()
        }
        assertEquals(TestConstants.Subjects.PHYSICS_ID, selectedSubject?.id)
    }

    @Test
    fun `put student enrollment selection subject not in enrollment`() = runTest {
        transaction {
            Subjects.insert {
                it[id] = TestConstants.Subjects.OTHER_ID
                it[name] = "Other Subject"
                it[code] = "OTH999"
                it[tag] = SubjectTag.MATH.number
                it[location] = "Room B"
                it[capacity] = 10
                it[group] = null
            }
        }

        val result = enrollmentSelectionService.setStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID,
            TestConstants.Subjects.OTHER_ID
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.CannotEnroll>(result)
        assertEquals(EnrollmentSelectionService.CanEnrollStatus.SUBJECT_NOT_IN_ENROLLMENT, result.status)
    }

    @Test
    fun `put student enrollment selection as teacher`() = runTest {
        val result = enrollmentSelectionService.setStudentSelection(
            bobSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.Success>(result)
    }

    @Test
    fun `put student enrollment selection as teacher not teaching subject`() = runTest {
        val result = enrollmentSelectionService.setStudentSelection(
            aliceSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.CannotModify>(result)
        assertEquals(EnrollmentSelectionService.ModifySelectionStatus.FORBIDDEN, result.status)
    }

    @Test
    fun `put student enrollment selection as teacher not teaching subject in enrollment`() = runTest {
        transaction {
            Enrollments.insert {
                it[id] = 3
                it[name] = "Other Enrollment"
                it[startDate] = null
                it[endDate] = null
                it[group] = TestConstants.Groups.GROUP_1_ID
            }
            EnrollmentSubjects.insert {
                it[enrollment] = 3
                it[subject] = TestConstants.Subjects.PHYSICS_ID
            }
            // Bob teaches Physics in SCIENCE_ID, but not in enrollment 3
        }

        val result = enrollmentSelectionService.setStudentSelection(
            bobSessionUser,
            TestConstants.Students.JOHN_ID,
            3,
            TestConstants.Subjects.PHYSICS_ID
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.CannotModify>(result)
        assertEquals(EnrollmentSelectionService.ModifySelectionStatus.FORBIDDEN, result.status)
    }

    @Test
    fun `delete student enrollment selection`() = runTest {
        enrollmentSelectionService.setStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        ).apply {
            assertIs<EnrollmentSelectionService.ModifySelectionResult.Success>(this, "Failed to set up selection")
        }

        val result = enrollmentSelectionService.deleteStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.Success>(result)

        val selections = transaction { enrollmentSelectionService.getStudentSelections(TestConstants.Students.JOHN_ID) }
        assertTrue(selections.isEmpty())
    }

    @Test
    fun `delete non existent selection`() = runTest {
        val result = enrollmentSelectionService.deleteStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.CannotModify>(result)
        assertEquals(
            EnrollmentSelectionService.ModifySelectionStatus.NOT_ENROLLED,
            result.status
        )
    }

    @Test
    fun `put student enrollment selection with invalid enrollment id`() = runTest {
        val result = enrollmentSelectionService.setStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            UNUSED_ID,
            TestConstants.Subjects.PHYSICS_ID
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.NotFound>(result)
        assertEquals(ExceptionEntity.ENROLLMENT, result.entity)
    }

    @Test
    fun `put student enrollment selection with invalid subject id`() = runTest {
        val result = enrollmentSelectionService.setStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID,
            UNUSED_ID,
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.NotFound>(result)
        assertEquals(ExceptionEntity.SUBJECT, result.entity)
    }

    @Test
    fun `put student enrollment selection as other student`() = runTest {
        val result = enrollmentSelectionService.setStudentSelection(
            janeSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.CannotModify>(result)
        assertEquals(EnrollmentSelectionService.ModifySelectionStatus.FORBIDDEN, result.status)
    }

    @Test
    fun `delete student enrollment selection as other student`() = runTest {
        enrollmentSelectionService.setStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        ).apply {
            assertIs<EnrollmentSelectionService.ModifySelectionResult.Success>(this, "Failed to set up selection")
        }

        val result = enrollmentSelectionService.deleteStudentSelection(
            janeSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.CannotModify>(result)
        assertEquals(EnrollmentSelectionService.ModifySelectionStatus.FORBIDDEN, result.status)
    }

    @Test
    fun `delete student enrollment selection as teacher`() = runTest {
        enrollmentSelectionService.setStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        ).apply {
            assertIs<EnrollmentSelectionService.ModifySelectionResult.Success>(this, "Failed to set up selection")
        }

        val result = enrollmentSelectionService.deleteStudentSelection(
            bobSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.Success>(result)

        val selections = transaction { enrollmentSelectionService.getStudentSelections(TestConstants.Students.JOHN_ID) }
        assertTrue(selections.isEmpty())
    }

    @Test
    fun `put student enrollment selection update existing`() = runTest {
        enrollmentSelectionService.setStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID,
            TestConstants.Subjects.PHYSICS_ID
        ).apply {
            assertIs<EnrollmentSelectionService.ModifySelectionResult.Success>(this, "Failed to set up selection")
        }

        val result = enrollmentSelectionService.setStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID,
            TestConstants.Subjects.CHEMISTRY_ID
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.CannotEnroll>(result)
        assertEquals(EnrollmentSelectionService.CanEnrollStatus.ALREADY_ENROLLED, result.status)
    }

    @Test
    fun `delete selection with invalid enrollment id`() = runTest {
        val result = enrollmentSelectionService.deleteStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            UNUSED_ID,
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.NotFound>(result)
        assertEquals(ExceptionEntity.ENROLLMENT, result.entity)
    }

    @Test
    fun `put student enrollment selection not in subject group`() = runTest {
        transaction {
            Subjects.insert {
                it[id] = TestConstants.Subjects.OTHER_ID
                it[name] = "Group Subject"
                it[code] = "TMS101"
                it[tag] = SubjectTag.MATH.number
                it[location] = "Room C"
                it[capacity] = 10
                it[group] = TestConstants.Groups.GROUP_2_ID
            }

            EnrollmentSubjects.insert {
                it[enrollment] = TestConstants.Enrollments.SCIENCE_ID
                it[subject] = TestConstants.Subjects.OTHER_ID
            }
        }

        val result = enrollmentSelectionService.setStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.SCIENCE_ID,
            TestConstants.Subjects.OTHER_ID,
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.CannotEnroll>(result)
        assertEquals(EnrollmentSelectionService.CanEnrollStatus.NOT_IN_SUBJECT_GROUP, result.status)
    }

    @Test
    fun `put student enrollment selection not in enrollment group`() = runTest {
        transaction {
            Subjects.insert {
                it[id] = TestConstants.Subjects.OTHER_ID
                it[name] = "Group Subject"
                it[code] = "TMS101"
                it[tag] = SubjectTag.MATH.number
                it[location] = "Room C"
                it[capacity] = 10
                it[group] = null
            }

            EnrollmentSubjects.insert {
                it[enrollment] = TestConstants.Enrollments.SCIENCE_ID
                it[subject] = TestConstants.Subjects.OTHER_ID
            }
        }

        // JANE is in GROUP_2, SCIENCE enrollment requires GROUP_1
        val result = enrollmentSelectionService.setStudentSelection(
            janeSessionUser,
            TestConstants.Students.JANE_ID,
            TestConstants.Enrollments.SCIENCE_ID,
            TestConstants.Subjects.OTHER_ID,
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.CannotEnroll>(result)
        assertEquals(EnrollmentSelectionService.CanEnrollStatus.NOT_IN_ENROLLMENT_GROUP, result.status)
    }

    @Test
    fun `put student enrollment selection in date ranges`() = runTest {
        transaction {
            Enrollments.insert {
                it[id] = TestConstants.Enrollments.OUT_OF_DATE_ID
                it[name] = TestConstants.Enrollments.OUT_OF_DATE_NAME
                it[startDate] = LocalDateTime.now().minusSeconds(5)
                it[endDate] = LocalDateTime.now().minusSeconds(1)
            }

            EnrollmentSubjects.insert {
                it[enrollment] = TestConstants.Enrollments.OUT_OF_DATE_ID
                it[subject] = TestConstants.Subjects.PHYSICS_ID
            }
        }

        val result1 = enrollmentSelectionService.setStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.OUT_OF_DATE_ID,
            TestConstants.Subjects.PHYSICS_ID,
        )

        transaction {
            Enrollments.update({ Enrollments.id eq TestConstants.Enrollments.OUT_OF_DATE_ID }) {
                it[startDate] = LocalDateTime.now().plusSeconds(5)
                it[endDate] = LocalDateTime.now().plusSeconds(10)
            }
        }

        val result2 = enrollmentSelectionService.setStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.OUT_OF_DATE_ID,
            TestConstants.Subjects.PHYSICS_ID,
        )

        listOf(result1, result2).forEach { result ->
            assertIs<EnrollmentSelectionService.ModifySelectionResult.CannotEnroll>(result)
            assertEquals(EnrollmentSelectionService.CanEnrollStatus.NOT_IN_ENROLLMENT_DATE_RANGE, result.status)
        }

        transaction {
            Enrollments.update({ Enrollments.id eq TestConstants.Enrollments.OUT_OF_DATE_ID }) {
                it[startDate] = LocalDateTime.now()
                it[endDate] = LocalDateTime.now().plusSeconds(10)
            }
        }

        val result3 = enrollmentSelectionService.setStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.OUT_OF_DATE_ID,
            TestConstants.Subjects.PHYSICS_ID,
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.Success>(result3)
    }

    @Test
    fun `teachers cannot bypass date range checks`() = runTest {
        transaction {
            Enrollments.insert {
                it[id] = TestConstants.Enrollments.OUT_OF_DATE_ID
                it[name] = TestConstants.Enrollments.OUT_OF_DATE_NAME
                it[startDate] = LocalDateTime.now().minusDays(2)
                it[endDate] = LocalDateTime.now().minusDays(1)
            }

            EnrollmentSubjects.insert {
                it[enrollment] = TestConstants.Enrollments.OUT_OF_DATE_ID
                it[subject] = TestConstants.Subjects.PHYSICS_ID
            }

            TeacherSubjects.insert {
                it[teacher] = TestConstants.Teachers.BOB_ID
                it[enrollment] = TestConstants.Enrollments.OUT_OF_DATE_ID
                it[subject] = TestConstants.Subjects.PHYSICS_ID
            }
        }

        val result = enrollmentSelectionService.setStudentSelection(
            bobSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.OUT_OF_DATE_ID,
            TestConstants.Subjects.PHYSICS_ID,
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.CannotEnroll>(result)
        assertEquals(EnrollmentSelectionService.CanEnrollStatus.NOT_IN_ENROLLMENT_DATE_RANGE, result.status)
    }

    @Test
    fun `admins can bypass date range checks`() = runTest {
        transaction {
            Enrollments.insert {
                it[id] = TestConstants.Enrollments.OUT_OF_DATE_ID
                it[name] = TestConstants.Enrollments.OUT_OF_DATE_NAME
                it[startDate] = LocalDateTime.now().minusDays(2)
                it[endDate] = LocalDateTime.now().minusDays(1)
            }

            EnrollmentSubjects.insert {
                it[enrollment] = TestConstants.Enrollments.OUT_OF_DATE_ID
                it[subject] = TestConstants.Subjects.PHYSICS_ID
            }
        }

        val result = enrollmentSelectionService.setStudentSelection(
            charlieAdminSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.OUT_OF_DATE_ID,
            TestConstants.Subjects.PHYSICS_ID,
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.Success>(result)
    }

    @Test
    fun `students cannot delete selection out of date range`() = runTest {
        transaction {
            Enrollments.insert {
                it[id] = TestConstants.Enrollments.OUT_OF_DATE_ID
                it[name] = TestConstants.Enrollments.OUT_OF_DATE_NAME
                it[startDate] = LocalDateTime.now().minusDays(2)
                it[endDate] = LocalDateTime.now().minusDays(1)
            }

            EnrollmentSubjects.insert {
                it[enrollment] = TestConstants.Enrollments.OUT_OF_DATE_ID
                it[subject] = TestConstants.Subjects.PHYSICS_ID
            }
        }

        // Force set selection since it's out of date
        enrollmentSelectionService.forceSetAllStudentSelections(
            TestConstants.Students.JOHN_ID,
            mapOf(TestConstants.Enrollments.OUT_OF_DATE_ID to TestConstants.Subjects.PHYSICS_ID)
        )

        val result = enrollmentSelectionService.deleteStudentSelection(
            johnSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.OUT_OF_DATE_ID
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.CannotEnroll>(result)
        assertEquals(EnrollmentSelectionService.CanEnrollStatus.NOT_IN_ENROLLMENT_DATE_RANGE, result.status)
    }

    @Test
    fun `admins can delete selection out of date range`() = runTest {
        transaction {
            Enrollments.insert {
                it[id] = TestConstants.Enrollments.OUT_OF_DATE_ID
                it[name] = TestConstants.Enrollments.OUT_OF_DATE_NAME
                it[startDate] = LocalDateTime.now().minusDays(2)
                it[endDate] = LocalDateTime.now().minusDays(1)
            }

            EnrollmentSubjects.insert {
                it[enrollment] = TestConstants.Enrollments.OUT_OF_DATE_ID
                it[subject] = TestConstants.Subjects.PHYSICS_ID
            }
        }

        // Force set selection since it's out of date
        enrollmentSelectionService.forceSetAllStudentSelections(
            TestConstants.Students.JOHN_ID,
            mapOf(TestConstants.Enrollments.OUT_OF_DATE_ID to TestConstants.Subjects.PHYSICS_ID)
        )

        val result = enrollmentSelectionService.deleteStudentSelection(
            charlieAdminSessionUser,
            TestConstants.Students.JOHN_ID,
            TestConstants.Enrollments.OUT_OF_DATE_ID
        )

        assertIs<EnrollmentSelectionService.ModifySelectionResult.Success>(result)
    }

    @Test
    fun `force set all student selections`() = runTest {
        enrollmentSelectionService.forceSetAllStudentSelections(
            TestConstants.Students.JOHN_ID,
            mapOf(TestConstants.Enrollments.SCIENCE_ID to TestConstants.Subjects.PHYSICS_ID)
        )

        val selections = transaction {
            enrollmentSelectionService.getStudentSelections(TestConstants.Students.JOHN_ID)
                .mapValues { it.value.toProto() }
        }

        assertEquals(TestConstants.Subjects.PHYSICS_ID, selections[TestConstants.Enrollments.SCIENCE_ID]?.id)
    }

    @Test
    fun `force set all student selections replaces existing`() = runTest {
        enrollmentSelectionService.forceSetAllStudentSelections(
            TestConstants.Students.JOHN_ID,
            mapOf(TestConstants.Enrollments.SCIENCE_ID to TestConstants.Subjects.PHYSICS_ID)
        )

        enrollmentSelectionService.forceSetAllStudentSelections(
            TestConstants.Students.JOHN_ID,
            mapOf()
        )

        val selections = transaction {
            enrollmentSelectionService.getStudentSelections(TestConstants.Students.JOHN_ID)
        }

        assertTrue(selections.isEmpty())
    }

    @Test
    fun `force set all student selections with invalid student`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            enrollmentSelectionService.forceSetAllStudentSelections(
                UNUSED_ID,
                mapOf(TestConstants.Enrollments.SCIENCE_ID to TestConstants.Subjects.PHYSICS_ID)
            )
        }
    }

    @Test
    fun `force set all student selections with invalid enrollment`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            enrollmentSelectionService.forceSetAllStudentSelections(
                TestConstants.Students.JOHN_ID,
                mapOf(UNUSED_ID to TestConstants.Subjects.PHYSICS_ID)
            )
        }
    }

    @Test
    fun `force set all student selections with invalid subject`() = runTest {
        assertFailsWith<EntityNotFoundException> {
            enrollmentSelectionService.forceSetAllStudentSelections(
                TestConstants.Students.JOHN_ID,
                mapOf(TestConstants.Enrollments.SCIENCE_ID to UNUSED_ID)
            )
        }
    }

    @Test
    fun `force set all student selections with subject not in enrollment`() = runTest {
        transaction {
            Subjects.insert {
                it[id] = TestConstants.Subjects.OTHER_ID
                it[name] = "Other Subject"
                it[code] = "OTH999"
                it[tag] = SubjectTag.MATH.number
                it[location] = "Room B"
                it[capacity] = 10
                it[group] = null
            }
        }

        assertFailsWith<IllegalArgumentException> {
            enrollmentSelectionService.forceSetAllStudentSelections(
                TestConstants.Students.JOHN_ID,
                mapOf(TestConstants.Enrollments.SCIENCE_ID to TestConstants.Subjects.OTHER_ID)
            )
        }
    }
}
