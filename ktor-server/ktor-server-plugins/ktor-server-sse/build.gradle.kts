description = "Server-sent events (SSE) support"

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-shared:ktor-sse"))
        }
    }
    commonTest {
        dependencies {
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx:ktor-serialization-kotlinx-json"))
        }
    }
}
