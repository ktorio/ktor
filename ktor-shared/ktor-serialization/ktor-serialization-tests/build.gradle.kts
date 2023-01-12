plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation:ktor-client-content-negotiation-tests")) // ktlint-disable max-line-length

            api(libs.logback.classic)
        }
    }
}
