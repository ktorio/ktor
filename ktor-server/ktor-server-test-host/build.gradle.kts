description = ""

val jetty_alpn_boot_version: String? by extra

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(project(":ktor-server:ktor-server-host-common"))
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-client:ktor-client-core"))
            api(project(":ktor-client:ktor-client-cio"))
            api(project(":ktor-test-dispatcher"))
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

            api(libs.jetty.http2.client)
            api(libs.jetty.client)
            api(libs.jetty.http2.client.transport)

            if (jetty_alpn_boot_version != null) {
                api(libs.jetty.alpn.boot)
            }

            api(libs.junit)
            implementation(libs.kotlinx.coroutines.debug)
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
        }
    }

    jvmAndNixTest {
        dependencies {
            api(project(":ktor-server:ktor-server-config-yaml"))
        }
    }
}
