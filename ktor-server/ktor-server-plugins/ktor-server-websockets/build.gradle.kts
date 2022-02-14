description = ""

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-shared:ktor-websockets"))
            api(project(":ktor-shared:ktor-websocket-serialization"))
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-content-negotiation"))
            api(project(":ktor-server:ktor-server-jetty"))
            api(project(":ktor-server:ktor-server-netty"))
            api(project(":ktor-server:ktor-server-tomcat"))
            api(project(":ktor-server:ktor-server-cio"))
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
        }
    }
}
