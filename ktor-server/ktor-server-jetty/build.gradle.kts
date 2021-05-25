description = ""
val jetty_version: String by extra

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(project(":ktor-server:ktor-server-host-common"))
                api(project(":ktor-server:ktor-server-servlet"))
                api("org.eclipse.jetty:jetty-server:$jetty_version")
                api("org.eclipse.jetty:jetty-servlets:$jetty_version")
                api("org.eclipse.jetty:jetty-alpn-server:$jetty_version")
                api("org.eclipse.jetty:jetty-alpn-openjdk8-server:$jetty_version")
                api("org.eclipse.jetty:jetty-alpn-java-server:$jetty_version")
                api("org.eclipse.jetty.http2:http2-server:$jetty_version")
            }
        }
        val jvmTest by getting {
            dependencies {
                api(project(":ktor-server:ktor-server-core"))
                api(project(":ktor-server:ktor-server-test-host"))
                api(project(":ktor-server:ktor-server-test-suites"))
                api("org.eclipse.jetty:jetty-servlet:$jetty_version")

                api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            }
        }
    }
}

val jetty_alpn_boot_version: String? by extra

dependencies {
    if (jetty_alpn_boot_version != null) {
        add("boot", "org.mortbay.jetty.alpn:alpn-boot:$jetty_alpn_boot_version")
    }
}
