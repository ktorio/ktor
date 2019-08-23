
val serialization_version: String by project.extra

kotlin.sourceSets {
    darwinMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
    darwinTest {
        dependencies {
            api(project(":ktor-client:ktor-client-features:ktor-client-logging"))
            api(project(":ktor-client:ktor-client-features:ktor-client-json"))
            api("org.jetbrains.kotlinx:kotlinx-serialization-runtime-native:$serialization_version")
        }
    }
}
