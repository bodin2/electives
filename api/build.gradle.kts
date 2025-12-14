val kotlin_version: String by extra
val logback_version: String by project

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin") version "3.3.2"
}

group = "th.ac.bodin2.electives.api"
version = "1.0.0"

application {
    mainClass = "th.ac.bodin2.electives.api.ApplicationKt"
}

tasks.test {
    systemProperty("APP_ENV", "test")
}

dependencies {
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-conditional-headers")
    implementation("io.ktor:ktor-server-forwarded-header")
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-sessions")
    implementation("io.ktor:ktor-server-resources")
    implementation("io.ktor:ktor-server-websockets")
    implementation("io.ktor:ktor-server-body-limit")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.github.flaxoos:ktor-server-rate-limiting:2.2.1")
    implementation("io.ktor:ktor-server-cio")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")

    implementation(project(":common"))

    implementation("com.mayakapps.kache:kache:2.1.1")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("com.h2database:h2:2.2.224")
}