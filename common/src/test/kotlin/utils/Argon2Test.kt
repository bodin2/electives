package th.ac.bodin2.electives.utils

import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class Argon2Test {
    private val argon2 = Argon2(memory = 16384, iterations = 10)

    private val password = "testpassword123".toCharArray()

    @Test
    fun `hashing works`() {
        val hash = argon2.hash(password)

        assertNotNull(hash)
        assertTrue(hash.isNotBlank())
        assertTrue(hash.startsWith($$"$argon2"))
    }

    @Test
    fun `verifying works`() {
        val hash = argon2.hash(password)

        val result = argon2.verify(hash, password)
        assertTrue(result)
    }

    @Test
    fun `incorrect password doesn't verify`() {
        val wrongPassword = "wrongpassword".toCharArray()
        val hash = argon2.hash(password)

        val result = argon2.verify(hash, wrongPassword)
        assertFalse(result)
    }

    @Test
    fun `hashes are different due to salting`() {
        val hash1 = argon2.hash(password)
        val hash2 = argon2.hash(password)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `both hashes verify`() {
        val hash1 = argon2.hash(password)
        val hash2 = argon2.hash(password)

        assertTrue(argon2.verify(hash1, password))
        assertTrue(argon2.verify(hash2, password))
    }

    @Test
    fun `finding iterations works`() {
        val iterations = Argon2.findIterations(maxTime = 50.milliseconds)
        assertTrue(iterations > 0)
    }

    @Test
    fun `hashing empty string works`() {
        val empty = "".toCharArray()
        val hash = argon2.hash(empty)
        assertNotNull(hash)
        assertTrue(hash.isNotBlank())
        assertTrue(argon2.verify(hash, empty))
    }

    @Test
    fun `hashing long string works`() {
        val longPassword = "a".repeat(10000).toCharArray()
        val hash = argon2.hash(longPassword)
        assertNotNull(hash)
        assertTrue(argon2.verify(hash, longPassword))
    }

    @Test
    fun `hashing with special characters works`() {
        val specialPassword = "  密碼123🔐 @émoji!   ".toCharArray()
        val hash = argon2.hash(specialPassword)
        assertTrue(argon2.verify(hash, specialPassword))
    }

    @Test
    fun `spaces are significant when hashing strings`() {
        val password = "   testpassword   "
        val trimmedPassword = password.trim().toCharArray()
        val hash = argon2.hash(password.toCharArray())
        assertTrue(argon2.verify(hash, password.toCharArray()))
        assertFalse(argon2.verify(hash, trimmedPassword))
    }

    @Test
    fun `an empty hash shouldn't verify`() {
        val result = argon2.verify("", password)
        assertFalse(result)
    }
}

