description = ""

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(project(":ktor-server:ktor-server-host-common"))
                api(project(":ktor-server:ktor-server-servlet"))
                api(libs.jetty.server)
                api(libs.jetty.servlets)
                api(libs.jetty.alpn.server)
                api(libs.jetty.alpn.openjdk8.server)
                api(libs.jetty.alpn.java.server)
                api(libs.jetty.http2.server)
            }
        }
        val jvmTest by getting {
            dependencies {
                api(project(":ktor-server:ktor-server-core"))
                api(project(":ktor-server:ktor-server-test-host"))
                api(project(":ktor-server:ktor-server-test-suites"))
                api(libs.jetty.servlet)

                api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            }
        }
    }
}

val need_alpn_boot: Boolean by extra

dependencies {
    if (need_alpn_boot) {
        add("boot", libs.jetty.alpn.boot)
    }
}
