description = ""

val need_alpn_boot: Boolean by extra

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-server:ktor-server-host-common"))
            api(project(":ktor-network:ktor-network-tls"))
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
            api(project(":ktor-client:ktor-client-core"))
            api(project(":ktor-client:ktor-client-jetty"))
            api(project(":ktor-client:ktor-client-cio"))

            // Not ideal, but prevents an additional artifact, and this is usually just included for testing,
            // so shouldn"t increase the size of the final artifact.
            api(project(":ktor-features:ktor-websockets"))

            api(libs.logback.classic)
            api(libs.jetty.client)
            api(libs.jetty.http2.client)
            api(libs.jetty.http2.client.transport)

            if (need_alpn_boot) {
                api(libs.jetty.alpn.boot)
            }

            api(libs.junit)
            api(libs.kotlinx.coroutines.debug)
        }
    }

    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            api(libs.kotlin.test.junit)
        }
    }
}
