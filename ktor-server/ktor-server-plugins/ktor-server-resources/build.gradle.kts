description = "Server side Resources feature"

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(project(":ktor-shared:ktor-resources"))
            api(libs.kotlinx.serialization.core)
        }
    }
}
