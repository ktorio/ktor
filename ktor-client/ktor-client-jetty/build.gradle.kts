description = "Jetty based client engine"

val jetty_version: String by project.extra

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))

            api(libs.jetty.http2.client)
            api(libs.jetty.alpn.openjdk8.client)
            api(libs.jetty.alpn.java.client)
        }
    }
    commonTest {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
        }
    }
}
