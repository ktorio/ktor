kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-server-features:ktor-server-auth"))
            api(project(":ktor-server-features:ktor-server-data-conversion"))
        }
    }
}
