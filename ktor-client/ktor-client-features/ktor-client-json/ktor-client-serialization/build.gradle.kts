
val serialization_version = extra["serialization_version"]

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    val commonMain by getting {
        dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization_version")
            api(project(":ktor-client:ktor-client-features:ktor-client-json"))
        }
    }
}
