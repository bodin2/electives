package th.ac.bodin2.electives.utils

import kotlin.test.*

class Argon2Test {
    @BeforeTest
    fun setupEach() {
        System.setProperty("APP_ENV", "test")
        Argon2.init(memory = 16384, iterations = 10)
    }

    @Test
    fun testHashPassword() {
        val password = "testpassword123".toCharArray()
        val hash = Argon2.hash(password)

        assertNotNull(hash)
        assertTrue(hash.isNotBlank())
        assertTrue(hash.startsWith($$"$argon2"))
    }

    @Test
    fun testVerifyPasswordSuccess() {
        val password = "testpassword123".toCharArray()
        val hash = Argon2.hash(password)

        val result = Argon2.verify(hash.toByteArray(), password)
        assertTrue(result)
    }

    @Test
    fun testVerifyPasswordFailure() {
        val password = "testpassword123".toCharArray()
        val wrongPassword = "wrongpassword".toCharArray()
        val hash = Argon2.hash(password)

        val result = Argon2.verify(hash.toByteArray(), wrongPassword)
        assertFalse(result)
    }

    @Test
    fun testHashesAreDifferent() {
        val password = "testpassword123".toCharArray()
        val hash1 = Argon2.hash(password)
        val hash2 = Argon2.hash(password)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun testBothHashesVerify() {
        val password = "testpassword123".toCharArray()
        val hash1 = Argon2.hash(password)
        val hash2 = Argon2.hash(password)

        assertTrue(Argon2.verify(hash1.toByteArray(), password))
        assertTrue(Argon2.verify(hash2.toByteArray(), password))
    }

    @Test
    fun testFindIterations() {
        val iterations = Argon2.findIterations(50)
        assertTrue(iterations > 0)
    }

    @Test
    fun testHashEmptyPassword() {
        val password = "".toCharArray()
        val hash = Argon2.hash(password)
        assertNotNull(hash)
        assertTrue(hash.isNotBlank())
        assertTrue(Argon2.verify(hash.toByteArray(), password))
    }

    @Test
    fun testHashVeryLongPassword() {
        val password = "a".repeat(10000).toCharArray()
        val hash = Argon2.hash(password)
        assertNotNull(hash)
        assertTrue(Argon2.verify(hash.toByteArray(), password))
    }

    @Test
    fun testHashPasswordWithSpecialCharacters() {
        val password = "  密碼123🔐 @émoji!   ".toCharArray()
        val hash = Argon2.hash(password)
        assertTrue(Argon2.verify(hash.toByteArray(), password))
    }

    @Test
    fun testHashPasswordSignificantWhitespaces() {
        val password = "   testpassword   "
        val trimmedPassword = password.trim().toCharArray()
        val hash = Argon2.hash(password.toCharArray())
        assertTrue(Argon2.verify(hash.toByteArray(), password.toCharArray()))
        assertFalse(Argon2.verify(hash.toByteArray(), trimmedPassword))
    }

    @Test
    fun testVerifyWithEmptyHash() {
        val password = "testpassword".toCharArray()
        val result = Argon2.verify(ByteArray(0), password)
        assertFalse(result)
    }
}

