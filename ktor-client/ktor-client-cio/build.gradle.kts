description = "CIO backend for ktor http client"

val logback_version: String by project.extra

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
                api(project(":ktor-http:ktor-http-cio"))
                api(project(":ktor-network:ktor-network-tls"))
            }
        }
        val commonTest by getting {
            dependencies {
                api(project(":ktor-client:ktor-client-tests"))
            }
        }
        val jvmTest by getting {
            dependencies {
                api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
            }
        }
    }
}
