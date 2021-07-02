kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-server-plugins:ktor-server-auth"))
            api(project(":ktor-server-plugins:ktor-server-data-conversion"))
        }
    }
}
