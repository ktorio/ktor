description = "Serialization API for client and server"

kotlin.sourceSets {
    val commonMain by getting {
        dependencies {
            api(project(":ktor-shared:ktor-websockets"))
        }
    }
}
