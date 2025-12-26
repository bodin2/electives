package th.ac.bodin2.electives.api.annotations

/**
 * Indicates that the annotated function creates a new database transaction.
 * This is used to mark functions that should be called outside an existing transaction context.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@RequiresOptIn
annotation class CreatesTransaction