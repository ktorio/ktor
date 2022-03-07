description = ""

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":ktor-server:ktor-server-test-host"))

            implementation(libs.kotlinx.coroutines.debug)
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
        }
    }
}
