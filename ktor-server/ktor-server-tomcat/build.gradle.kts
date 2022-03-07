description = ""

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-host-common"))
            api(project(":ktor-server:ktor-server-servlet"))
            api(libs.tomcat.catalina)
            api(libs.tomcat.embed.core)
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
