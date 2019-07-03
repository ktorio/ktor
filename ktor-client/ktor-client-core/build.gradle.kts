import org.jetbrains.kotlin.gradle.plugin.*

description = "Ktor http client"

val ideaActive: Boolean by project
val coroutines_version: String by project

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-http:ktor-http-cio"))
        }
    }

    jvmMain {
        dependencies {
            api(project(":ktor-network"))
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
