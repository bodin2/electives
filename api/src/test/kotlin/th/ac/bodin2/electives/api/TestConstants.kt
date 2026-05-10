package th.ac.bodin2.electives.api

import io.mockk.every
import io.mockk.mockk
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.proto.api.UserType

object TestConstants {
    object Students {
        const val JOHN_ID = 1001
        const val JOHN_FIRST_NAME = "John"
        const val JOHN_MIDDLE_NAME = "M"
        const val JOHN_LAST_NAME = "Doe"
        const val JOHN_PASSWORD = "password123"

        const val JANE_ID = 1002
        const val JANE_FIRST_NAME = "Jane"
        const val JANE_LAST_NAME = "Smith"
        const val JANE_PASSWORD = "password456"
    }

    object Teachers {
        const val BOB_ID = 2001
        const val BOB_FIRST_NAME = "Bob"
        const val BOB_LAST_NAME = "Teacher"
        const val BOB_PASSWORD = "teachpass123"

        const val ALICE_ID = 2002
        const val ALICE_FIRST_NAME = "Alice"
        const val ALICE_MIDDLE_NAME = "T"
        const val ALICE_LAST_NAME = "Professor"
        const val ALICE_PASSWORD = "teachpass456"
    }

    object Admins {
        const val CHARLIE_ID = 3001
        const val CHARLIE_PASSWORD = "adminpass123"
    }

    object Groups {
        const val GROUP_1_ID = 1
        const val GROUP_1_NAME = "Team 1"

        const val GROUP_2_ID = 2
        const val GROUP_2_NAME = "Team 2"
    }

    object Enrollments {
        const val SCIENCE_ID = 1
        const val SCIENCE_NAME = "Science Elective"

        const val OUT_OF_DATE_ID = 2
        const val OUT_OF_DATE_NAME = "Outdated Elective"
    }

    object Subjects {
        const val PHYSICS_ID = 101
        const val PHYSICS_NAME = "Physics"
        const val PHYSICS_CODE = "PHY101"
        const val PHYSICS_DESCRIPTION = "Introduction to Physics"
        const val PHYSICS_LOCATION = "Room A101"
        const val PHYSICS_CAPACITY = 30

        const val CHEMISTRY_ID = 102
        const val CHEMISTRY_NAME = "Chemistry"
        const val CHEMISTRY_CODE = "CHE101"
        const val CHEMISTRY_DESCRIPTION = "Introduction to Chemistry"
        const val CHEMISTRY_LOCATION = "Room A102"
        const val CHEMISTRY_CAPACITY = 25

        const val OTHER_ID = 999
    }

    object TestData {
        const val CLIENT_NAME = "test-client"
    }

    object Limits {
        const val PASSWORD_TOO_LONG = 4097
        const val CLIENT_NAME_TOO_LONG = 257
    }
}

object SessionUserMocks {
    val johnSessionUser: UsersService.SessionUser = mockk()
    val janeSessionUser: UsersService.SessionUser = mockk()
    val bobSessionUser: UsersService.SessionUser = mockk()
    val aliceSessionUser: UsersService.SessionUser = mockk()
    val charlieAdminSessionUser: UsersService.SessionUser = mockk()

    init {
        every { johnSessionUser.id } returns TestConstants.Students.JOHN_ID
        every { johnSessionUser.type } returns UserType.STUDENT

        every { janeSessionUser.id } returns TestConstants.Students.JANE_ID
        every { janeSessionUser.type } returns UserType.STUDENT

        every { bobSessionUser.id } returns TestConstants.Teachers.BOB_ID
        every { bobSessionUser.type } returns UserType.TEACHER

        every { aliceSessionUser.id } returns TestConstants.Teachers.ALICE_ID
        every { aliceSessionUser.type } returns UserType.TEACHER

        every { charlieAdminSessionUser.id } returns TestConstants.Admins.CHARLIE_ID
        every { charlieAdminSessionUser.type } returns UserType.ADMIN
    }
}