package th.ac.bodin2.electives

data class NotFoundException(override val message: String? = null) : Exception(message)