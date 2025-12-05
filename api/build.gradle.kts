val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.21"
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

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("org.jetbrains.exposed:exposed-core:0.53.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.53.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.53.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.53.0")

    implementation("org.xerial:sqlite-jdbc:3.46.0.0")

    implementation("com.google.protobuf:protobuf-java:4.28.2")
    implementation("th.ac.bodin2.electives:proto")

    implementation("io.github.nbaars:paseto4j-version4:2024.3")
    // @TODO: Use the version without prebuilt binaries and include setup instructions
    implementation("de.mkammerer:argon2-jvm:2.12")

    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
    implementation("io.ktor:ktor-client-auth:3.3.2")

    implementation("com.mayakapps.kache:kache:2.1.1")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}