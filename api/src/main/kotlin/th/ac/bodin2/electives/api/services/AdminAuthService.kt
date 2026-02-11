package th.ac.bodin2.electives.api.services

interface AdminAuthService {
    /**
     * Creates and returns a random challenge for authentication.
     */
    fun newChallenge(): String

    /**
     * Creates a new admin session, returning the session token, invalidating the old session and the challenge.
     *
     * @param signature A base64url-encoded signature of the current challenge.
     * @param ip The IP address of the requester.
     *
     * @throws IllegalArgumentException if the token or session is invalid.
     */
    suspend fun createSession(signature: String, ip: String): CreateSessionResult

    sealed class CreateSessionResult {
        data class Success(val token: String) : CreateSessionResult()
        data class IPNotAllowed(val ip: String) : CreateSessionResult()
        object InvalidSignature : CreateSessionResult()
        object NoChallenge : CreateSessionResult()
    }

    /**
     * Returns whether the token is still a valid session
     */
    fun hasSession(token: String, ip: String): Boolean

    /**
     * Clears the admin session.
     */
    fun clearSession()
}