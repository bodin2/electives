package th.ac.bodin2.electives

class EntityNotFoundException(val entity: ExceptionEntity, override val message: String = "${entity.displayName} not found") :
    Exception(message)

enum class ExceptionEntity(val displayName: String) {
    ENROLLMENT("Enrollment"),
    SUBJECT("Subject"),
    USER("User"),
    STUDENT("Student"),
    TEACHER("Teacher"),
    ENROLLMENT_SELECTION("Enrollment selection"),
    GROUP("Group"),
}

class ConflictException(val entity: ExceptionEntity, override val message: String = "${entity.displayName} already exists") :
    Exception(message)

class NothingToUpdateException : IllegalArgumentException("Nothing to update")
