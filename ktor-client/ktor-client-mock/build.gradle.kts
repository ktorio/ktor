
val serialization_version: String by project.extra

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-client:ktor-client-core"))
        }
    }

    jvmTest {
        dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serialization_version")
            api(project(":ktor-client:ktor-client-features:ktor-client-json"))
            api(project(":ktor-client:ktor-client-features:ktor-client-json:ktor-client-serialization"))
        }
    }
}
