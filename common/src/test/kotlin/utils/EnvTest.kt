package th.ac.bodin2.electives.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EnvTest {
    @Test
    fun testGetEnvFromSystemProperty() {
        System.setProperty("TEST_VAR", "test_value")
        val value = env("TEST_VAR")
        assertEquals("test_value", value)
    }

    @Test
    fun testGetEnvNotExists() {
        val value = env("NON_EXISTENT_VAR")
        assertNull(value)
    }

    @Test
    fun testGetEnvWithEmptyString() {
        System.setProperty("EMPTY_VAR", "")

        val value = env("EMPTY_VAR")

        assertEquals("", value)
    }

    @Test
    fun testGetEnvWithEmptyKey() {
        assertFailsWith<IllegalArgumentException> {
            env("")
        }
    }

    @Test
    fun testGetEnvWithWhitespaceInKey() {
        System.setProperty("KEY WITH SPACES", "value")
        val value = env("KEY WITH SPACES")
        assertEquals("value", value)
    }

    @Test
    fun testGetUnsetEnvWithRequireEnv() {
        assertFailsWith<IllegalStateException> {
            requireEnv("UNSET_VAR")
        }
    }

    @Test
    fun testGetBlankEnvWithRequireEnvNonBlank() {
        System.setProperty("BLANK_VAR", "")
        assertFailsWith<IllegalStateException> {
            requireEnvNonBlank("BLANK_VAR")
        }
    }
}

