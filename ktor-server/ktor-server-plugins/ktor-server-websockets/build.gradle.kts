description = ""

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(project(":ktor-shared:ktor-websockets"))
            api(project(":ktor-shared:ktor-websocket-serialization"))
        }
    }

    jvmAndNixTest {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-content-negotiation"))
            api(project(":ktor-server:ktor-server-cio"))
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-jetty"))
            api(project(":ktor-server:ktor-server-netty"))
            api(project(":ktor-server:ktor-server-tomcat"))
        }
    }
}
