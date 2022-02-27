plugins {
    id("kotlinx-serialization")
}

val logback_version: String by project.extra

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(kotlin("test"))
            api(kotlin("test-annotations-common"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx"))
            api(project(":ktor-client:ktor-client-tests"))
        }
    }
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation:ktor-client-content-negotiation-tests"))

            api("ch.qos.logback:logback-classic:$logback_version")
        }
    }
}
