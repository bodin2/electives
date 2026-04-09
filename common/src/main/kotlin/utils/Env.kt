package th.ac.bodin2.electives.utils

import io.github.cdimascio.dotenv.Dotenv

fun loadDotEnv() {
    val dotenv = Dotenv.configure()
        .ignoreIfMissing()
        .load()

    dotenv.entries().forEach { System.setProperty(it.key, it.value) }
}

fun env(key: String): String? {
    require(!key.isBlank()) { "Cannot get environment variable with blank key" }
    return System.getenv(key) ?: System.getProperty(key)
}

fun requireEnv(key: String): String =
    checkNotNull(env(key)) { "Required environment variable '$key' is not set" }

fun requireEnvNonBlank(key: String): String {
    val value = requireEnv(key)
    check(!value.isBlank()) { "Required environment variable '$key' is blank" }
    return value
}