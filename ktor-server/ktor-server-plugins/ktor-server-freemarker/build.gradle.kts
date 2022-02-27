kotlin.sourceSets {
    jvmMain {
        dependencies {
            api("org.freemarker:freemarker:[2.3.20, 2.4)")
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-compression"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))
        }
    }
}
