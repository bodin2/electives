package th.ac.bodin2.electives.api

import kotlinx.coroutines.runBlocking
import th.ac.bodin2.electives.api.services.AdminAuthService.CreateSessionResult
import th.ac.bodin2.electives.api.services.AdminAuthServiceImpl
import th.ac.bodin2.electives.utils.CIDR
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class AdminAuthServiceImplTest {
    companion object {
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()

        private val publicKeySpec = X509EncodedKeySpec(keyPair.public.encoded)

        private fun sign(challenge: String): String {
            val challengeBytes = Base64.getUrlDecoder().decode(challenge)
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initSign(keyPair.private)
            sig.update(challengeBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign())
        }

        init {
            setupTestEnvironment()
        }
    }

    private fun createService(
        sessionDurationSeconds: Long = 60,
        allowedIPs: List<CIDR>? = null,
    ) = AdminAuthServiceImpl(
        AdminAuthServiceImpl.Config(
            sessionDurationSeconds = sessionDurationSeconds,
            minimumSessionCreationTime = 0.seconds,
            publicKey = publicKeySpec,
            allowedIPs = allowedIPs,
        )
    )

    @Test
    fun `new challenge returns non-empty string`() {
        val service = createService()
        val challenge = service.newChallenge()
        assertTrue(challenge.isNotBlank())
    }

    @Test
    fun `new challenge returns different values`() {
        val service = createService()
        val c1 = service.newChallenge()
        val c2 = service.newChallenge()
        assertNotEquals(c1, c2)
    }

    @Test
    fun `create session with valid signature`() = runBlocking {
        val service = createService()
        val challenge = service.newChallenge()
        val signature = sign(challenge)

        val result = service.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result)
        assertTrue(result.token.isNotBlank())
    }

    @Test
    fun `create session with invalid signature`(): Unit = runBlocking {
        val service = createService()
        service.newChallenge()

        val result = service.createSession("invalid-signature", "127.0.0.1")
        assertIs<CreateSessionResult.InvalidSignature>(result)
    }

    @Test
    fun `create session without challenge`(): Unit = runBlocking {
        val service = createService()
        // Don't call newChallenge()

        val result = service.createSession("some-signature", "127.0.0.1")
        assertIs<CreateSessionResult.NoChallenge>(result)
    }

    @Test
    fun `create session invalidates challenge`(): Unit = runBlocking {
        val service = createService()
        val challenge = service.newChallenge()
        val signature = sign(challenge)

        val result1 = service.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result1)

        // Second attempt with same signature should fail since challenge was consumed
        val result2 = service.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.NoChallenge>(result2)
    }

    @Test
    fun `has session with valid token`() = runBlocking {
        val service = createService()
        val challenge = service.newChallenge()
        val signature = sign(challenge)

        val result = service.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result)

        assertTrue(service.hasSession(result.token, "127.0.0.1"))
    }

    @Test
    fun `has session with invalid token`() = runBlocking {
        val service = createService()
        val challenge = service.newChallenge()
        val signature = sign(challenge)

        service.createSession(signature, "127.0.0.1")

        assertFalse(service.hasSession("invalid-token", "127.0.0.1"))
    }

    @Test
    fun `has session after clear`() = runBlocking {
        val service = createService()
        val challenge = service.newChallenge()
        val signature = sign(challenge)

        val result = service.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result)

        service.clearSession()

        assertFalse(service.hasSession(result.token, "127.0.0.1"))
    }

    @Test
    fun `has session with no session created`() {
        val service = createService()
        assertFalse(service.hasSession("any-token", "127.0.0.1"))
    }

    @Test
    fun `create session with disallowed IP`(): Unit = runBlocking {
        val service = createService(allowedIPs = listOf(CIDR.parse("10.0.0.0/8")))
        val challenge = service.newChallenge()
        val signature = sign(challenge)

        val result = service.createSession(signature, "192.168.1.1")
        assertIs<CreateSessionResult.IPNotAllowed>(result)
    }

    @Test
    fun `create session with allowed IP`(): Unit = runBlocking {
        val service = createService(allowedIPs = listOf(CIDR.parse("127.0.0.0/8")))
        val challenge = service.newChallenge()
        val signature = sign(challenge)

        val result = service.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result)
    }

    @Test
    fun `has session with disallowed IP`() = runBlocking {
        val service = createService(allowedIPs = listOf(CIDR.parse("127.0.0.0/8")))
        val challenge = service.newChallenge()
        val signature = sign(challenge)

        val result = service.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result)

        // Token is valid but IP is not allowed
        assertFalse(service.hasSession(result.token, "192.168.1.1"))
        // Token is valid and IP is allowed
        assertTrue(service.hasSession(result.token, "127.0.0.1"))
    }

    @Test
    fun `session expires`() = runBlocking {
        val service = createService(sessionDurationSeconds = 1)
        val challenge = service.newChallenge()
        val signature = sign(challenge)

        val result = service.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result)

        // Wait for expiry
        kotlinx.coroutines.delay(1001L)

        assertFalse(service.hasSession(result.token, "127.0.0.1"))
    }

    @Test
    fun `new session replaces old session`() = runBlocking {
        val service = createService()

        val challenge1 = service.newChallenge()
        val signature1 = sign(challenge1)
        val result1 = service.createSession(signature1, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result1)

        val challenge2 = service.newChallenge()
        val signature2 = sign(challenge2)
        val result2 = service.createSession(signature2, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result2)

        // Old token should no longer work
        assertFalse(service.hasSession(result1.token, "127.0.0.1"))
        // New token should work
        assertTrue(service.hasSession(result2.token, "127.0.0.1"))
    }

    @Test
    fun `clear session`() = runBlocking {
        val service = createService()
        val challenge = service.newChallenge()
        val signature = sign(challenge)

        val result = service.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result)

        service.clearSession()

        assertFalse(service.hasSession(result.token, "127.0.0.1"))
    }
}
