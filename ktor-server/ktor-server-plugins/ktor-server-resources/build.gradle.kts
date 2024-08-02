description = "Server side Resources feature"

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    jvmAndPosixMain {
        dependencies {
            api(project(":ktor-shared:ktor-resources"))
            api(libs.kotlinx.serialization.core)
        }
    }
}
