package th.ac.bodin2.electives.api.services

import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.TestConstants
import th.ac.bodin2.electives.api.adminKeyPair
import th.ac.bodin2.electives.db.models.Admins
import th.ac.bodin2.electives.db.models.Users
import th.ac.bodin2.electives.proto.api.UserType
import th.ac.bodin2.electives.utils.Argon2
import th.ac.bodin2.electives.utils.CIDR
import java.security.Signature
import java.util.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class AdminAuthServiceImplTest : ApplicationTest() {
    companion object {
        private const val ADMIN_USER_ID = 9001

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

    private val ApplicationTestBuilder.usersService: UsersService
        get() {
            val service: UsersService by application.dependencies
            return service
        }

    override fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) {
        runTestWithCustomConfig(block = block)
    }

    private fun runTestWithCustomConfig(
        challengeTimeoutSeconds: Long = 60,
        sessionDurationSeconds: Long = 3600,
        allowedIPs: List<CIDR>? = null,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) {
        super.runTest(
            {
                application {
                    dependencies {
                        provide<AdminAuthService> {
                            AdminAuthServiceImpl(
                                config = AdminAuthServiceImpl.Config(
                                    minimumSessionCreationTime = 0.milliseconds,
                                    allowedIPs = allowedIPs,
                                    challengeTimeoutSeconds = challengeTimeoutSeconds,
                                    sessionDurationSeconds = sessionDurationSeconds,
                                ),
                                usersService = resolve<UsersService>(),
                            )
                        }
                    }
                }
            },
            block,
        )
    }

    private fun ApplicationTestBuilder.insertAdminUser(
        id: Int = ADMIN_USER_ID,
        publicKey: String = Base64.getEncoder().encodeToString(adminKeyPair.public.encoded),
    ) {
        val argon2: Argon2 by application.dependencies

        transaction {
            Users.insert {
                it[Users.id] = id
                it[Users.firstName] = "Admin"
                it[Users.middleName] = null
                it[Users.lastName] = "User"
                it[Users.avatarUrl] = null
                it[Users.passwordHash] = argon2.hash("admin-password".toCharArray())
                it[Users.sessionHash] = null
                it[Users.sessionExpiry] = null
            }

            Admins.insert {
                it[Admins.id] = id
                it[Admins.publicKey] = publicKey
            }
        }
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
    fun `create session with valid signature returns admin session`() = runTest {
        insertAdminUser()
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        val result = adminAuthService.createSession(
            id = ADMIN_USER_ID,
            signature = signature,
            aud = "test-client",
            ip = "127.0.0.1",
        )
        assertIs<AdminAuthService.CreateSessionResult.Success>(result)
        assertTrue(result.token.isNotBlank())

        val sessionUser = transaction { usersService.getSessionUser(result.token) }
        assertEquals(ADMIN_USER_ID, sessionUser.id)
        assertEquals(UserType.ADMIN, sessionUser.type)
    }

    @Test
    fun `create session with invalid signature should clear challenge`() = runTest {
        insertAdminUser()
        adminAuthService.newChallenge()

        val result = adminAuthService.createSession(
            id = ADMIN_USER_ID,
            signature = "invalid-signature",
            aud = "test-client",
            ip = "127.0.0.1",
        )
        assertIs<AdminAuthService.CreateSessionResult.InvalidSignature>(result)

        val result2 = adminAuthService.createSession(
            id = ADMIN_USER_ID,
            signature = "invalid-signature",
            aud = "test-client",
            ip = "127.0.0.1",
        )
        assertIs<AdminAuthService.CreateSessionResult.NoChallenge>(result2)
    }

    @Test
    fun `create session without challenge`() = runTest {
        insertAdminUser()

        val result = adminAuthService.createSession(
            id = ADMIN_USER_ID,
            signature = "some-signature",
            aud = "test-client",
            ip = "127.0.0.1",
        )
        assertIs<AdminAuthService.CreateSessionResult.NoChallenge>(result)
    }

    @Test
    fun `create session invalidates challenge after success`() = runTest {
        insertAdminUser()
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        val result1 = adminAuthService.createSession(
            id = ADMIN_USER_ID,
            signature = signature,
            aud = "test-client",
            ip = "127.0.0.1",
        )
        assertIs<AdminAuthService.CreateSessionResult.Success>(result1)

        val result2 = adminAuthService.createSession(
            id = ADMIN_USER_ID,
            signature = signature,
            aud = "test-client",
            ip = "127.0.0.1",
        )
        assertIs<AdminAuthService.CreateSessionResult.NoChallenge>(result2)
    }

    @Test
    fun `create session with disallowed IP`() = runTestWithCustomConfig(
        allowedIPs = listOf(CIDR.parse("10.0.0.0/8")),
    ) {
        insertAdminUser()
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        val result = adminAuthService.createSession(
            id = ADMIN_USER_ID,
            signature = signature,
            aud = "test-client",
            ip = "192.168.1.1",
        )
        assertIs<AdminAuthService.CreateSessionResult.IPNotAllowed>(result)
    }

    @Test
    fun `create session with user that is not admin`() = runTest {
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        val result = adminAuthService.createSession(
            id = TestConstants.Teachers.BOB_ID,
            signature = signature,
            aud = "test-client",
            ip = "127.0.0.1",
        )
        assertIs<AdminAuthService.CreateSessionResult.UserNotAdmin>(result)
    }

    @Test
    fun `challenge clears after timeout`() = runTestWithCustomConfig(challengeTimeoutSeconds = 1) {
        insertAdminUser()
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        delay(1200.milliseconds)

        val result = adminAuthService.createSession(
            id = ADMIN_USER_ID,
            signature = signature,
            aud = "test-client",
            ip = "127.0.0.1",
        )
        assertIs<AdminAuthService.CreateSessionResult.NoChallenge>(result)
    }

    @Test
    fun `permits only set IPs`() = runTestWithCustomConfig(
        allowedIPs = listOf(CIDR.parse("1.2.3.4/8"))
    ) {
        assertTrue(adminAuthService.permitsIP("1.2.3.4"))
        assertFalse(adminAuthService.permitsIP("5.6.7.8"))
    }

    @Test
    fun `session expires after duration`() = runTestWithCustomConfig(sessionDurationSeconds = 1) {
        insertAdminUser()
        val challenge = adminAuthService.newChallenge()
        val signature = sign(challenge)

        val result = adminAuthService.createSession(
            id = ADMIN_USER_ID,
            signature = signature,
            aud = "test-client",
            ip = "127.0.0.1",
        )

        assertIs<AdminAuthService.CreateSessionResult.Success>(result)

        delay(1200.milliseconds)

        assertFailsWith<IllegalArgumentException> {
            transaction { usersService.getSessionUser(result.token) }
        }
    }
}