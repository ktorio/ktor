description = "Jetty based client engine"

val jetty_version: String by project.extra

kotlin.sourceSets.jvmMain {
    dependencies {
        api(project(":ktor-client:ktor-client-core"))

        api("org.eclipse.jetty.http2:http2-client:$jetty_version")
        api("org.eclipse.jetty:jetty-alpn-openjdk8-client:$jetty_version")
        api("org.eclipse.jetty:jetty-alpn-java-client:$jetty_version")
    }
}
