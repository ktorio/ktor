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

    commonTest {
        dependencies {
            api(project(":ktor-test-dispatcher"))
        }
    }

    jvmTest {
        dependencies {
            api(libs.kotlinx.serialization.core)
            api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx:ktor-serialization-kotlinx-json"))
        }
    }
}
