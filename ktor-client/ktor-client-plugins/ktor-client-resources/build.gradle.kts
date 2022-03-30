description = "Client side Resources feature"

plugins {
    id("kotlinx-serialization")
}

val serialization_version: String by project.extra

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-shared:ktor-resources"))
            api(libs.kotlinx.serialization.core)
        }
    }
}
