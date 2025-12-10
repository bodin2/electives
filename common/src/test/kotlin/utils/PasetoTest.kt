package th.ac.bodin2.electives.utils

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.time.OffsetDateTime
import kotlin.test.*

class PasetoTest {
    @BeforeTest
    fun setupEach() {
        Security.addProvider(BouncyCastleProvider())

        Paseto.init(
            Paseto.loadPublicKey("MCowBQYDK2VwAyEAve+YNChVkaFQwynmkoR0K63Ep3Jtds7Ud//7aTg1Kec="),
            Paseto.loadPrivateKey("MC4CAQAwBQYDK2VwBCIEIAdX5xpM5fLhFERZW9ASIatBKBVC2yT8ieZKkWZy2H6Z"),
            "test-iss"
        )
    }

    @Test
    fun testSignToken() {
        val claims = PasetoClaims.from(
            iss = Paseto.ISSUER,
            sub = "1001",
            aud = "test-client",
            exp = OffsetDateTime.now().plusHours(1)
        )

        val token = Paseto.sign(claims)

        assertNotNull(token)
        assertTrue(token.isNotBlank())
        assertTrue(token.startsWith("v4.public."))
    }

    @Test
    fun testVerifyTokenSuccess() {
        val claims = PasetoClaims.from(
            iss = Paseto.ISSUER,
            sub = "1001",
            aud = "test-client",
            exp = OffsetDateTime.now().plusHours(1)
        )

        val token = Paseto.sign(claims)
        val verifiedClaims = Paseto.verify(token)

        assertEquals(claims.iss, verifiedClaims.iss)
        assertEquals(claims.sub, verifiedClaims.sub)
        assertEquals(claims.aud, verifiedClaims.aud)
    }

    @Test
    fun testVerifyExpiredToken() {
        val claims = PasetoClaims.from(
            iss = Paseto.ISSUER,
            sub = "1001",
            aud = "test-client",
            exp = OffsetDateTime.now().minusHours(1)
        )

        val token = Paseto.sign(claims)

        assertFailsWith<IllegalArgumentException> {
            Paseto.verify(token)
        }
    }

    @Test
    fun testVerifyInvalidIssuer() {
        val claims = PasetoClaims(
            iss = "wrong-issuer",
            sub = "1001",
            aud = "test-client",
            exp = OffsetDateTime.now().plusHours(1).toString(),
            iat = OffsetDateTime.now().toString()
        )

        val token = Paseto.sign(claims)

        assertFailsWith<IllegalArgumentException> {
            Paseto.verify(token)
        }
    }

    @Test
    fun testVerifyInvalidToken() {
        assertFailsWith<Exception> {
            Paseto.verify("invalid-token")
        }
    }

    @Test
    fun testVerifyTokenBeforeNbf() {
        val claims = PasetoClaims.from(
            iss = Paseto.ISSUER,
            sub = "1001",
            aud = "test-client",
            exp = OffsetDateTime.now().plusHours(2),
            nbf = OffsetDateTime.now().plusHours(1)
        )

        val token = Paseto.sign(claims)

        assertFailsWith<IllegalArgumentException> {
            Paseto.verify(token)
        }
    }

    @Test
    fun testPasetoClaimsFromEmptyIssuer() {
        assertFailsWith<IllegalArgumentException> {
            PasetoClaims.from(
                iss = "",
                sub = "1001",
                aud = "test-client",
                exp = OffsetDateTime.now().plusHours(1)
            )
        }
    }

    @Test
    fun testPasetoClaimsFromEmptySubject() {
        assertFailsWith<IllegalArgumentException> {
            PasetoClaims.from(
                iss = Paseto.ISSUER,
                sub = "",
                aud = "test-client",
                exp = OffsetDateTime.now().plusHours(1)
            )
        }
    }

    @Test
    fun testPasetoClaimsFromEmptyAudience() {
        assertFailsWith<IllegalArgumentException> {
            PasetoClaims.from(
                iss = Paseto.ISSUER,
                sub = "1001",
                aud = "",
                exp = OffsetDateTime.now().plusHours(1)
            )
        }
    }

    @Test
    fun testVerifyTokenAtExactExpiry() {
        val expTime = OffsetDateTime.now().plusSeconds(1)
        val claims = PasetoClaims.from(
            iss = Paseto.ISSUER,
            sub = "1001",
            aud = "test-client",
            exp = expTime
        )
        val token = Paseto.sign(claims)
        Thread.sleep(1100)
        assertFailsWith<IllegalArgumentException> {
            Paseto.verify(token)
        }
    }

    @Test
    fun testVerifyTokenWithNbfEqualsIat() {
        val now = OffsetDateTime.now()
        val claims = PasetoClaims.from(
            iss = Paseto.ISSUER,
            sub = "1001",
            aud = "test-client",
            exp = now.plusHours(1),
            nbf = now
        )
        val token = Paseto.sign(claims)
        val verifiedClaims = Paseto.verify(token)
        assertEquals("1001", verifiedClaims.sub)
    }
}

