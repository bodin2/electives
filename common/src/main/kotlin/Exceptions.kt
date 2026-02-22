package th.ac.bodin2.electives

class NotFoundException(val entity: ExceptionEntity, override val message: String = "${entity.name} not found") :
    Exception(message)

enum class ExceptionEntity {
    ELECTIVE,
    SUBJECT,
    USER,
    STUDENT,
    TEACHER,
    ELECTIVE_SELECTION,
    TEAM,
}

class ConflictException(val entity: ExceptionEntity, override val message: String = "${entity.name} already exists") :
    Exception(message)

class NothingToUpdateException : IllegalArgumentException("Nothing to update")