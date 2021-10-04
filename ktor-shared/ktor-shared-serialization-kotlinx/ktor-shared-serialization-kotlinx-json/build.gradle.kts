val serialization_version: String by project.extra

description = "Ktor JSON Content Negotiation via kotlinx.serialization support"

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-shared:ktor-shared-serialization-kotlinx"))
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
        }
    }
    commonTest {
        dependencies {
            api(project(":ktor-shared:ktor-shared-serialization-kotlinx:ktor-shared-serialization-kotlinx-tests"))
        }
    }
}
