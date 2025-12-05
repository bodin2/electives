import com.google.protobuf.gradle.id

plugins {
    kotlin("jvm") version "2.2.20"
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    implementation("com.google.protobuf:protobuf-kotlin-lite:4.28.2")
    implementation("com.google.protobuf:protobuf-java:4.28.2")
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

group = "th.ac.bodin2.electives"
version = "1.0.0"