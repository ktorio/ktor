description = "Serialization API for client and server"

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-shared:ktor-websockets"))
        }
    }
}
