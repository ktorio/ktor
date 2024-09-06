description = ""

ext {
    set(RELOCATED_ARTIFACT, "ktor-jackson")
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":ktor-shared:ktor-serialization"))
                api(libs.jackson.databind)
                api(libs.jackson.module.kotlin)
                implementation(libs.kotlin.reflect)
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-server:ktor-server-test-host"))
                api(project(":ktor-client:ktor-client-tests"))
                api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation:ktor-client-content-negotiation-tests")) // ktlint-disable max-line-length
                api(project(":ktor-shared:ktor-serialization:ktor-serialization-tests"))
                
                api(libs.logback.classic)
            }
        }
    }
}
