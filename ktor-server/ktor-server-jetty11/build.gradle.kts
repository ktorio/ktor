description = ""

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":ktor-server:ktor-server-host-common"))
                api(project(":ktor-server:ktor-server-servlet5"))
                api(libs.jetty11.server)
                api(libs.jetty11.servlets)
                api(libs.jetty11.alpn.server)
                api(libs.jetty11.alpn.java.server)
                api(libs.jetty11.http2.server)
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-server:ktor-server-core"))
                api(project(":ktor-server:ktor-server-test-host"))
                api(project(":ktor-server:ktor-server-test-suites"))

                api(libs.jetty.servlet)
                api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
                api(libs.logback.classic)
            }
        }
    }
}
