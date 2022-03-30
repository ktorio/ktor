description = ""

val kotlin_version: String by project.extra
val jackson_version: String by project.extra
val jackson_kotlin_version: String by project.extra
val logback_version: String by project.extra

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

                api(libs.logback.classic)
            }
        }
    }
}
