package th.ac.bodin2.electives

data class NotFoundException(val entity: NotFoundEntity, override val message: String? = "${entity.name} not found", ) : Exception(message)
enum class NotFoundEntity {
    ELECTIVE,
    SUBJECT,
    USER,
    STUDENT,
    TEACHER,
    ELECTIVE_SELECTION,
}