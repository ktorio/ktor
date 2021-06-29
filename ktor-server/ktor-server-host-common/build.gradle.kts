description = ""

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-server:ktor-server"))
            api(project(":ktor-http:ktor-http-cio"))
        }
    }
}
