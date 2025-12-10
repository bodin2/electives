package th.ac.bodin2.electives.utils

import kotlin.test.*

class EnvTest {
    @BeforeTest
    fun setupEach() {
        ENV_PREFIX = ""
        ENV_GET_FROM_SYSTEM = true
    }

    @Test
    fun testGetEnvFromSystemProperty() {
        System.setProperty("TEST_VAR", "test_value")
        val value = getEnv("TEST_VAR")
        assertEquals("test_value", value)
    }

    @Test
    fun testGetEnvNotExists() {
        val value = getEnv("NON_EXISTENT_VAR")
        assertNull(value)
    }

    @Test
    fun testGetEnvWithPrefix() {
        ENV_PREFIX = "APP_"
        System.setProperty("APP_TEST_VAR", "prefixed_value")

        val value = getEnv("TEST_VAR")

        assertEquals("prefixed_value", value)
    }

    @Test
    fun testGetEnvFromSystemPropertyOnly() {
        ENV_GET_FROM_SYSTEM = false
        System.setProperty("PROP_ONLY_VAR", "property_value")

        val value = getEnv("PROP_ONLY_VAR")

        assertEquals("property_value", value)
    }

    @Test
    fun testGetEnvWithEmptyString() {
        System.setProperty("EMPTY_VAR", "")

        val value = getEnv("EMPTY_VAR")

        assertEquals("", value)
    }

    @Test
    fun testEnvPrefixPersists() {
        ENV_PREFIX = "MY_PREFIX_"
        System.setProperty("MY_PREFIX_KEY", "value1")

        val value1 = getEnv("KEY")
        assertEquals("value1", value1)

        System.setProperty("MY_PREFIX_KEY2", "value2")
        val value2 = getEnv("KEY2")
        assertEquals("value2", value2)
    }

    @Test
    fun testGetEnvSystemPropertyOverridesSystemEnv() {
        ENV_GET_FROM_SYSTEM = true
        System.setProperty("OVERRIDE_VAR", "from_property")

        val value = getEnv("OVERRIDE_VAR")

        assertEquals("from_property", value)
    }

    @Test
    fun testGetEnvWithEmptyKey() {
        assertFailsWith<IllegalArgumentException> {
            getEnv("")
        }
    }

    @Test
    fun testGetEnvWithWhitespaceInKey() {
        System.setProperty("KEY WITH SPACES", "value")
        val value = getEnv("KEY WITH SPACES")
        assertEquals("value", value)
    }
}

