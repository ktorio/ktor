description = ""

val gson_version: String by project.extra
val kotlin_version: String by project.extra

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-shared:ktor-shared-serialization"))
            api("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
            api("com.google.code.gson:gson:$gson_version")
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
