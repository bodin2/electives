package th.ac.bodin2.electives

class EntityNotFoundException(val entity: ExceptionEntity, override val message: String = "${entity.name} not found") :
    Exception(message)

enum class ExceptionEntity {
    ENROLLMENT,
    SUBJECT,
    USER,
    STUDENT,
    TEACHER,
    ENROLLMENT_SELECTION,
    GROUP,
}

class ConflictException(val entity: ExceptionEntity, override val message: String = "${entity.name} already exists") :
    Exception(message)

class NothingToUpdateException : IllegalArgumentException("Nothing to update")
