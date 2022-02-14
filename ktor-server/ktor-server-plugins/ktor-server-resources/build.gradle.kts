description = "Server side Resources feature"

plugins {
    id("kotlinx-serialization")
}

val serialization_version: String by project.extra
val logback_version: String by project.extra

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(project(":ktor-shared:ktor-resources"))
            api("org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization_version")
        }
    }
    jvmAndNixTest {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
        }
    }

    jvmTest {
        dependencies {
            api("ch.qos.logback:logback-classic:$logback_version")
        }
    }
}
