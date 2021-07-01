description = ""

val tomcat_version: String by extra

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-host-common"))
            api(project(":ktor-server:ktor-server-servlet"))
            api("org.apache.tomcat:tomcat-catalina:$tomcat_version")
            api("org.apache.tomcat.embed:tomcat-embed-core:$tomcat_version")
        }
        val jvmTest by getting {
            dependencies {
                api(project(":ktor-server:ktor-server-test-host"))
                api(project(":ktor-server:ktor-server-test-suites"))
                api(project(":ktor-server:ktor-server-core"))
                api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            }
        }
    }
}
