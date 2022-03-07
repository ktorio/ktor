description = "Ktor http client"

kotlin.sourceSets {
    val commonMain by getting {
        dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-http:ktor-http-cio"))
        }
    }

    val jsMain by getting {
        dependencies {
            api(npm("node-fetch", libs.versions.node.fetch.version.get()))
            api(npm("abort-controller", libs.versions.abort.controller.version.get()))
            api(npm("ws", libs.versions.ws.version.get()))
        }
    }

    val commonTest by getting {
        dependencies {
            api(project(":ktor-test-dispatcher"))
            api(project(":ktor-client:ktor-client-mock"))
        }
    }

    val jvmTest by getting {
        dependencies {
            implementation(libs.kotlinx.coroutines.debug)
        }
    }
}
