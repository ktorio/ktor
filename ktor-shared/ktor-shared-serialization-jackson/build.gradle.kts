description = ""

val jackson_version: String by project.extra
val jackson_kotlin_version: String by project.extra

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":ktor-shared:ktor-shared-serialization"))
                api("com.fasterxml.jackson.core:jackson-databind:$jackson_version")
                api("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_kotlin_version")
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-server:ktor-server-test-host"))
                api(project(":ktor-client:ktor-client-tests"))
                api(project(":ktor-client:ktor-client-features:ktor-client-content-negotiation:ktor-client-content-negotiation-tests"))
            }
        }
    }
}
