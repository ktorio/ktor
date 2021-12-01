description = ""

val jetty_alpn_boot_version: String? by extra
val logback_version: String by extra
val jetty_version: String by extra
val coroutines_version: String by extra
val junit_version: String by extra
val kotlin_version: String by extra

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(project(":ktor-server:ktor-server-host-common"))
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-client:ktor-client-core"))
            api(project(":ktor-client:ktor-client-cio"))
        }
    }

    jvmMain {
        dependencies {
            api(project(":ktor-network:ktor-network-tls"))

            api(project(":ktor-client:ktor-client-jetty"))
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-call-logging"))

            // Not ideal, but prevents an additional artifact, and this is usually just included for testing,
            // so shouldn"t increase the size of the final artifact.
            api(project(":ktor-server:ktor-server-plugins:ktor-server-websockets"))

            api("ch.qos.logback:logback-classic:$logback_version")
            api("org.eclipse.jetty.http2:http2-client:$jetty_version")
            api("org.eclipse.jetty:jetty-client:$jetty_version")
            api("org.eclipse.jetty.http2:http2-http-client-transport:$jetty_version")

            if (jetty_alpn_boot_version != null) {
                api("org.mortbay.jetty.alpn:alpn-boot:$jetty_alpn_boot_version")
            }

            api("junit:junit:$junit_version")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutines_version")

            // https://github.com/Kotlin/kotlinx.coroutines/issues/3001
            val jna_version = "5.10.0"
            api("net.java.dev.jna:jna:$jna_version")
            api("net.java.dev.jna:jna-platform:$jna_version")
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            api("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
            api("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
        }
    }
}
