package th.ac.bodin2.electives.utils

import io.github.cdimascio.dotenv.Dotenv

fun loadDotEnv() {
    val dotenv = Dotenv.configure()
        .ignoreIfMissing()
        .load()

    dotenv.entries().forEach { System.setProperty(it.key, it.value) }
}

fun getEnv(key: String): String? =
    System.getenv(key) ?: System.getProperty(key)

val isTest = getEnv("APP_ENV") == "test"
val isDev = getEnv("APP_ENV") == "development"