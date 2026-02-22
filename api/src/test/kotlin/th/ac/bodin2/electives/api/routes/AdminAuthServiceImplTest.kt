package th.ac.bodin2.electives.api.routes

import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.adminKeyPair
import th.ac.bodin2.electives.api.services.AdminAuthService
import th.ac.bodin2.electives.api.services.AdminAuthService.CreateSessionResult
import th.ac.bodin2.electives.api.services.AdminAuthServiceImpl
import th.ac.bodin2.electives.utils.CIDR
import java.security.Signature
import java.util.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class AdminAuthServiceImplTest : ApplicationTest() {
    companion object {
        private fun sign(challenge: String): String {
            val challengeBytes = Base64.getUrlDecoder().decode(challenge)
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initSign(adminKeyPair.private)
            sig.update(challengeBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign())
        }
    }

    private val ApplicationTestBuilder.adminAuthService: AdminAuthService
        get() {
            val service: AdminAuthService by application.dependencies
            return service
        }

    override fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) {
        runTestWithCustomConfig(block = block)
    }

    private fun runTestWithCustomConfig(
        challengeTimeoutSeconds: Long = 60,
        sessionDurationSeconds: Long = 60,
        allowedIPs: List<CIDR>? = null,
        block: suspend ApplicationTestBuilder.() -> Unit
    ) {
        super.runTest(
            {
                application {
                    dependencies.provide<AdminAuthService> {
                        AdminAuthServiceImpl(
                            AdminAuthServiceImpl.Config(
                                sessionDurationSeconds = sessionDurationSeconds,
                                minimumSessionCreationTime = 0.milliseconds,
                                publicKey = adminKeyPair.public,
                                allowedIPs = allowedIPs,
                                challengeTimeoutSeconds = challengeTimeoutSeconds
                            )
                        )
                    }
                }
            },
            block
        )
    }

    @Test
    fun `new challenge returns non-empty string`() = runTest {
        val challenge = adminAuthService.newChallenge()
        assertTrue(challenge.isNotBlank())
    }

    @Test
    fun `new challenge returns different values`() = runTest {
        val c1 = adminAuthService.newChallenge()
        val c2 = adminAuthService.newChallenge()
        assertNotEquals(c1, c2)
    }

    @Test
    fun `create session with valid signature`() = runTest {
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        val result = adminAuthService.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result)
        assertTrue(result.token.isNotBlank())
    }

    @Test
    fun `create session with invalid signature`(): Unit = runTest {
        adminAuthService.newChallenge()

        val result = adminAuthService.createSession("invalid-signature", "127.0.0.1")
        assertIs<CreateSessionResult.InvalidSignature>(result)
    }

    @Test
    fun `create session with invalid signature should clear challenge`(): Unit = runTest {
        adminAuthService.newChallenge()

        val result = adminAuthService.createSession("invalid-signature", "127.0.0.1")
        assertIs<CreateSessionResult.InvalidSignature>(result)

        val result2 = adminAuthService.createSession("invalid-signature", "127.0.0.1")
        assertIs<CreateSessionResult.NoChallenge>(result2)
    }

    @Test
    fun `create session without challenge`(): Unit = runTest {
        // Don't call newChallenge()

        val result = adminAuthService.createSession("some-signature", "127.0.0.1")
        assertIs<CreateSessionResult.NoChallenge>(result)
    }

    @Test
    fun `create session invalidates challenge`(): Unit = runTest {
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        val result1 = adminAuthService.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result1)

        // Second attempt with same signature should fail since challenge was consumed
        val result2 = adminAuthService.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.NoChallenge>(result2)
    }

    @Test
    fun `has session with valid token`() = runTest {
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        val result = adminAuthService.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result)

        assertTrue(adminAuthService.hasSession(result.token, "127.0.0.1"))
    }

    @Test
    fun `has session with invalid token`() = runTest {
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        adminAuthService.createSession(signature, "127.0.0.1")

        assertFalse(adminAuthService.hasSession("invalid-token", "127.0.0.1"))
    }

    @Test
    fun `has session after clear`() = runTest {
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        val result = adminAuthService.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result)

        adminAuthService.clearSession()

        assertFalse(adminAuthService.hasSession(result.token, "127.0.0.1"))
    }

    @Test
    fun `has session with no session created`() = runTest {
        assertFalse(adminAuthService.hasSession("any-token", "127.0.0.1"))
    }

    @Test
    fun `create session with disallowed IP`(): Unit = runTestWithCustomConfig(
        allowedIPs = listOf(CIDR.parse("10.0.0.0/8"))
    ) {
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        val result = adminAuthService.createSession(signature, "192.168.1.1")
        assertIs<CreateSessionResult.IPNotAllowed>(result)
    }

    @Test
    fun `create session with allowed IP`(): Unit = runTestWithCustomConfig(
        allowedIPs = listOf(CIDR.parse("127.0.0.0/8"))
    ) {
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        val result = adminAuthService.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result)
    }

    @Test
    fun `has session with disallowed IP`() = runTestWithCustomConfig(
        allowedIPs = listOf(CIDR.parse("127.0.0.0/8"))
    ) {
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        val result = adminAuthService.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result)

        // Token is valid but IP is not allowed
        assertFalse(adminAuthService.hasSession(result.token, "192.168.1.1"))
        // Token is valid and IP is allowed
        assertTrue(adminAuthService.hasSession(result.token, "127.0.0.1"))
    }

    @Test
    fun `session expires`() = runTestWithCustomConfig(sessionDurationSeconds = 1) {
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        val result = adminAuthService.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result)

        assertTrue(adminAuthService.hasSession(result.token, "127.0.0.1"))

        delay(1250)

        assertFalse(adminAuthService.hasSession(result.token, "127.0.0.1"))
    }

    @Test
    fun `new session replaces old session`() = runTest {
        val challenge1 = adminAuthService.newChallenge()
        val signature1 = sign(challenge1)
        val result1 = adminAuthService.createSession(signature1, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result1)

        val challenge2 = adminAuthService.newChallenge()
        val signature2 = sign(challenge2)
        val result2 = adminAuthService.createSession(signature2, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result2)

        // Old token should no longer work
        assertFalse(adminAuthService.hasSession(result1.token, "127.0.0.1"))
        // New token should work
        assertTrue(adminAuthService.hasSession(result2.token, "127.0.0.1"))
    }

    @Test
    fun `clear session`() = runTest {
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        val result = adminAuthService.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.Success>(result)

        adminAuthService.clearSession()

        assertFalse(adminAuthService.hasSession(result.token, "127.0.0.1"))
    }

    @Test
    fun `challenge is unique and atomic`(): Unit = runTest {
        val challenge1 = adminAuthService.newChallenge()
        val challenge2 = adminAuthService.newChallenge()
        assertNotEquals(challenge1, challenge2)

        val signature1 = sign(challenge1)
        val result = adminAuthService.createSession(signature1, "127.0.0.1")
        assertIs<CreateSessionResult.InvalidSignature>(result)
    }

    @Test
    fun `challenge clears after timeout`(): Unit = runTestWithCustomConfig(challengeTimeoutSeconds = 1) {
        @OptIn(ExperimentalCoroutinesApi::class)
        kotlinx.coroutines.test.runTest {
            val challenge = adminAuthService.newChallenge()
            val signature = sign(challenge)

            advanceTimeBy(999)

            val result1 = adminAuthService.createSession(signature, "127.0.0.1")
            assertIs<CreateSessionResult.Success>(result1)

            advanceTimeBy(1)

            val result2 = adminAuthService.createSession(signature, "127.0.0.1")

            assertIs<CreateSessionResult.NoChallenge>(result2)
        }
    }

    @Test
    fun `challenges clear after failed creation`(): Unit = runTest {
        val challenge = adminAuthService.newChallenge()

        val result1 = adminAuthService.createSession("invalid-signature", "127.0.0.1")
        assertIs<CreateSessionResult.InvalidSignature>(result1)

        val signature = sign(challenge)
        val result2 = adminAuthService.createSession(signature, "127.0.0.1")
        assertIs<CreateSessionResult.NoChallenge>(result2)
    }
}
