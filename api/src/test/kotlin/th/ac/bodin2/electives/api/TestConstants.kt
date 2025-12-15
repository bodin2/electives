package th.ac.bodin2.electives.api

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

    object Teams {
        const val TEAM_1_ID = 1
        const val TEAM_1_NAME = "Team 1"

        const val TEAM_2_ID = 2
        const val TEAM_2_NAME = "Team 2"
    }

    object Electives {
        const val SCIENCE_ID = 1
        const val SCIENCE_NAME = "Science Elective"
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

        // Subject ids used in tests for special cases
        const val NO_TEACHER_ID = 103
        const val OTHER_ID = 999
    }

    object TestData {
        const val CLIENT_NAME = "test-client"
        const val INVALID_TOKEN = "invalid-token"
        const val NONEXISTENT_ID = 9999
        const val VERY_LARGE_ID = 999999999
        const val NEGATIVE_ID = -1
        const val ZERO_ID = 0
    }

    object Query {
        const val WITH_STUDENTS = "with_students"
    }

    // Lengths used for negative tests
    object Lengths {
        const val AUTH_PASSWORD_TOO_LONG = 1000
        const val AUTH_CLIENTNAME_TOO_LONG = 300
        const val USERS_PASSWORD_TOO_LONG = 4097
    }

    object Limits {
        const val PASSWORD_MAX = 4096
        const val CLIENTNAME_MAX = 256
        const val CLIENTNAME_TOO_LONG = 257
    }

    object Avatar {
        const val BYTES = 1024
    }
}
