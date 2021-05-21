description = "Shared functionality for client and server"

subprojects {
    kotlin.sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":ktor-http"))
            }
        }
    }
}
