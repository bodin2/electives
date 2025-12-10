package th.ac.bodin2.electives.utils

import io.github.cdimascio.dotenv.Dotenv

fun loadDotEnv() {
    val dotenv = Dotenv.configure()
        .ignoreIfMissing()
        .load()

    dotenv.entries().forEach { System.setProperty(it.key, it.value) }
}

fun getEnv(key: String): String? {
    val key = "$ENV_PREFIX$key"
    if (key.isBlank()) throw IllegalArgumentException("Cannot getEnv with blank key")

    return if (ENV_GET_FROM_SYSTEM) System.getenv(key) ?: System.getProperty(key)
    else System.getProperty(key)
}

/**
 * Prefix to add to all environment variable lookups.
 */
var ENV_PREFIX = ""

/**
 * Whether to get environment variables from the system environment variables.
 */
var ENV_GET_FROM_SYSTEM = true

val isTest = getEnv("APP_ENV") == "test"
val isDev = getEnv("APP_ENV") == "development"