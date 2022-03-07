
plugins {
    kotlin("plugin.serialization") version "1.6.10"
}

kotlin.sourceSets {
    val commonMain by getting {
        dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-client:ktor-client-core"))
        }
    }

    val jvmTest by getting {
        dependencies {
            api(libs.kotlinx.serialization.core)
            api(project(":ktor-client:ktor-client-features:ktor-client-json"))
            api(project(":ktor-client:ktor-client-features:ktor-client-json:ktor-client-serialization"))
        }
    }
}
