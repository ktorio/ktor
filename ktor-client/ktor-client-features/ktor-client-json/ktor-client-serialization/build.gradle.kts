
val serialization_version = extra["serialization_version"]

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    val commonMain by getting {
        dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$serialization_version")
            api(project(":ktor-client:ktor-client-features:ktor-client-json"))
        }
    }

    val jvmMain by getting {
        dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serialization_version")
        }
    }

    val jsMain by getting {
        dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-runtime-js:$serialization_version")
        }
    }
    val posixMain by getting {
        dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-runtime-native:$serialization_version")
        }
    }
}
