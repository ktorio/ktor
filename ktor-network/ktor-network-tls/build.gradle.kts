val netty_version: String by project.extra

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(project(":ktor-network"))
            api(project(":ktor-utils"))
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
            api("io.netty:netty-handler:$netty_version")
        }
    }
}
