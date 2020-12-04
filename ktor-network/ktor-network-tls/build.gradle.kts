val netty_version: String by project.extra

kotlin.sourceSets {
    val commonMain by getting {
        dependencies {
            api(project(":ktor-network"))
            api(project(":ktor-utils"))
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
            api("io.netty:netty-handler:$netty_version")
        }
    }
}
