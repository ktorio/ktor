description = ""

val netty_version: String by extra
val jetty_alpn_api_version: String by extra
val netty_tcnative_version: String by extra
val mockk_version: String by extra

val enableAlpnProp = project.hasProperty("enableAlpn")
val osName = System.getProperty("os.name").toLowerCase()
val nativeClassifier: String? = if (enableAlpnProp) {
    when {
        osName.contains("win") -> "windows-x86_64"
        osName.contains("linux") -> "linux-x86_64"
        osName.contains("mac") -> "osx-x86_64"
        else -> throw InvalidUserDataException("Unsupported os family $osName")
    }
} else null

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-host-common"))

            api("io.netty:netty-codec-http2:$netty_version")
            api("org.eclipse.jetty.alpn:alpn-api:$jetty_alpn_api_version")

            api("io.netty:netty-transport-native-kqueue:$netty_version")
            api("io.netty:netty-transport-native-epoll:$netty_version")
            if (nativeClassifier != null) {
                api("io.netty:netty-tcnative-boringssl-static:$netty_tcnative_version")
            }
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-server:ktor-server-test-suites"))
            api(project(":ktor-server:ktor-server-core"))

            api("io.netty:netty-tcnative:$netty_tcnative_version")
            api("io.netty:netty-tcnative-boringssl-static:$netty_tcnative_version")
            implementation("io.mockk:mockk:$mockk_version")
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
        }
    }
}
