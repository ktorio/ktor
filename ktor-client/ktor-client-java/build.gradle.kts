val coroutines_version: String by project.extra

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutines_version")
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
        }
    }
}
