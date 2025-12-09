import com.google.protobuf.gradle.id
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    api("com.google.protobuf:protobuf-kotlin-lite:4.28.2")
    api("com.google.protobuf:protobuf-java:4.28.2")

    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    api("org.jetbrains.exposed:exposed-core:0.53.0")
    api("org.jetbrains.exposed:exposed-dao:0.53.0")
    api("org.jetbrains.exposed:exposed-jdbc:0.53.0")
    api("org.jetbrains.exposed:exposed-java-time:0.53.0")

    implementation("org.xerial:sqlite-jdbc:3.46.0.0")

    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")

    implementation("io.github.nbaars:paseto4j-version4:2024.3")
    // @TODO: Use the version without prebuilt binaries and include setup instructions
    implementation("de.mkammerer:argon2-jvm:2.12")
    api("org.bouncycastle:bcpkix-jdk18on:1.83")
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("**/*.proto")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }

    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("kotlin")
            }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("src/main/proto")
        }

        java {
            srcDir("build/generated/source/proto/main/kotlin")
        }
    }
}

// Force UTF-8 encoding for Java compilation to handle non-ASCII (Thai) chars in generated sources
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.set(freeCompilerArgs.get() + listOf("-Xjsr305=strict"))
    }
}

group = "th.ac.bodin2.electives"
version = "1.0.0"