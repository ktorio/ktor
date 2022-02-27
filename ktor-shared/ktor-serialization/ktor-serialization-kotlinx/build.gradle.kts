val serialization_version: String by project.extra

description = "Ktor Content Negotiation via kotlinx.serialization support"

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-shared:ktor-serialization"))
            api("org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization_version")
        }
    }
}
