val node_fetch_version: String by project
val abort_controller_version: String by project
val ws_version: String by project

kotlin.sourceSets.jsMain {
    dependencies {
        api(project(":ktor-client:ktor-client-core"))

        api(npm("node-fetch", node_fetch_version))
        api(npm("abort-controller", abort_controller_version))
        api(npm("ws", ws_version))
    }
}
