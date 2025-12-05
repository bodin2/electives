package th.ac.bodin2.electives.api

data class NotFoundException(override val message: String? = null) : Exception(message)