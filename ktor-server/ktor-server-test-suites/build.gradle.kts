description = ""

val coroutines_version: String by extra

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            implementation(project(":ktor-server:ktor-server-test-host"))

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutines_version")
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration= "testOutput"))
        }
    }
}
