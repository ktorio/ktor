description = ""

val coroutines_version: String by extra

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":ktor-server:ktor-server-test-host"))
            implementation(project(":ktor-server-features:ktor-server-compression"))
            implementation(project(":ktor-server-features:ktor-server-partial-content"))
            implementation(project(":ktor-server-features:ktor-server-status-pages"))
            implementation(project(":ktor-server-features:ktor-server-conditional-headers"))
            implementation(project(":ktor-server-features:ktor-server-forwarded-header"))
            implementation(project(":ktor-server-features:ktor-server-auto-head-response"))

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutines_version")
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server:ktor-server", configuration = "testOutput"))
        }
    }
}
