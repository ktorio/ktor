val serialization_version: String by project.extra

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-shared:ktor-shared-serialization"))
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
            api("org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization_version")
        }
    }
    commonTest {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation:ktor-client-content-negotiation-tests"))
        }
    }
}
