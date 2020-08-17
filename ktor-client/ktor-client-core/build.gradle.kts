import org.jetbrains.kotlin.gradle.plugin.*

description = "Ktor http client"

val ideaActive: Boolean by project
val coroutines_version: String by project

val node_fetch_version: String by project
val abort_controller_version: String by project
val ws_version: String by project

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-http:ktor-http-cio"))
            api(project(":ktor-network"))
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
//            api(project(":ktor-client:ktor-client-tests"))
//            api(project(":ktor-client:ktor-client-features:ktor-client-logging"))
        }
    }

    jvmTest {
        dependencies {
//            api(project(":ktor-client:ktor-client-mock"))
//            api(project(":ktor-client:ktor-client-tests"))
//            api(project(":ktor-client:ktor-client-cio"))
//            api(project(":ktor-client:ktor-client-okhttp"))
//            api(project(":ktor-features:ktor-websockets"))
            
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutines_version")
        }
    }
}
