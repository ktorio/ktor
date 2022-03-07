
plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    val commonMain by getting {
        dependencies {
            api(libs.kotlinx.serialization.json)
            api(project(":ktor-client:ktor-client-features:ktor-client-json"))
        }
    }
}
