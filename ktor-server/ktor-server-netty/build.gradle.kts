description = ""

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

            api(libs.netty.codec.http2)
            api(libs.jetty.alpn.api)
            api(libs.netty.transport.native.kqueue)
            api(libs.netty.transport.native.epoll)

            if (nativeClassifier != null) {
                api(libs.netty.tcnative.boringssl.static)
            }
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-server:ktor-server-test-suites"))
            api(project(":ktor-server:ktor-server-core"))

            api(libs.netty.tcnative)
            api(libs.netty.tcnative.boringssl.static)
            implementation(libs.mockk)
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
        }
    }
}
