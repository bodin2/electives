import com.google.protobuf.gradle.id
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    alias(libs.plugins.protobuf)
}

dependencies {
    api(libs.protobuf.kotlin.lite)
    api(libs.protobuf.java)

    api(libs.exposed.core)
    api(libs.exposed.dao)
    api(libs.exposed.jdbc)
    api(libs.exposed.java.time)

    api(libs.sqlite.jdbc)
    implementation(libs.dotenv.kotlin)

    // @TODO: Use the version without prebuilt binaries and include setup instructions
    implementation(libs.argon2.jvm)
    implementation(libs.guava)

    testImplementation(libs.kotlin.test.junit)
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("**/*.proto")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.asProvider().get()}"
    }

    generateProtoTasks {
        all().forEach {
            it.builtins {
                id("kotlin") { option("lite") }
            }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("src/main/proto")
        }

        kotlin {
            srcDir("build/generated/sources/proto/main/kotlin")
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