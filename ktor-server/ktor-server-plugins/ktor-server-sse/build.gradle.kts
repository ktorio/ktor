description = "Server-sent events (SSE) support"

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(project(":ktor-shared:ktor-sse"))
        }
    }

    jvmAndNixTest {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
}
