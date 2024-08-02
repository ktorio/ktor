description = "Server-sent events (SSE) support"

kotlin.sourceSets {
    jvmAndPosixMain {
        dependencies {
            api(project(":ktor-shared:ktor-sse"))
        }
    }

    jvmAndPosixTest {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
}
