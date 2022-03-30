description = ""

val jansi_version: String by project.extra
val logback_version: String by project.extra

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-server"))
            api(project(":ktor-server:ktor-server-test-host"))
        }
    }
    jvmTest {
        dependencies {
            implementation(libs.jansi)
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            api(libs.logback.classic)
        }
    }
}
