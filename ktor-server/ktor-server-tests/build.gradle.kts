description = ""

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-server:ktor-server-test-host"))
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
        }
    }
}
