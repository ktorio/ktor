plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(kotlin("test"))
            api(kotlin("test-annotations-common"))
            api(project(":ktor-shared:ktor-shared-serialization-kotlinx"))
            api(project(":ktor-client:ktor-client-tests"))
        }
    }
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation:ktor-client-content-negotiation-tests"))
        }
    }
}
