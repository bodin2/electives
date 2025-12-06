rootProject.name = "electives"
include(":api")
include(":common")

pluginManagement {
    val kotlin_version: String by extra

    plugins {
        kotlin("jvm") version kotlin_version
        kotlin("plugin.serialization") version kotlin_version
    }
}