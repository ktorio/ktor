description = "Common tests for client-json"

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-features:ktor-client-json:ktor-client-gson"))
            api(project(":ktor-client:ktor-client-cio"))
            api(project(":ktor-client:ktor-client-tests"))
            api(project(":ktor-features:ktor-gson"))
        }
    }
}
