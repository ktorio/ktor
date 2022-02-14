description = "Ktor http client"

val coroutines_version: String by project

val node_fetch_version: String by project
val abort_controller_version: String by project
val ws_version: String by project

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-shared:ktor-events"))
            api(project(":ktor-shared:ktor-websocket-serialization"))
        }
    }

    jsMain {
        dependencies {
            api(npm("node-fetch", node_fetch_version))
            api(npm("abort-controller", abort_controller_version))
            api(npm("ws", ws_version))
        }
    }

    commonTest {
        dependencies {
            api(project(":ktor-test-dispatcher"))
            api(project(":ktor-client:ktor-client-mock"))
        }
    }
}
