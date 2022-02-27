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
                api("com.fasterxml.jackson.core:jackson-databind:$jackson_version")
                api("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_kotlin_version")
                implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-server:ktor-server-test-host"))
                api(project(":ktor-client:ktor-client-tests"))
                api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation:ktor-client-content-negotiation-tests"))

                api("ch.qos.logback:logback-classic:$logback_version")
            }
        }
    }
}
