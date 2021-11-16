description = "Common code for Resources feature"

val serialization_version: String by project.extra

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-utils"))
            api("org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization_version")
        }
    }
}
