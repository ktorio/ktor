description = ""

val jansi_version: String by project.extra

kotlin.sourceSets {
    val commonMain by getting {
        dependencies {
            api(project(":ktor-server"))
            api(project(":ktor-server:ktor-server-test-host"))
        }
    }
    val jvmTest by getting {
        dependencies {
            implementation("org.fusesource.jansi:jansi:$jansi_version")
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
        }
    }
}
