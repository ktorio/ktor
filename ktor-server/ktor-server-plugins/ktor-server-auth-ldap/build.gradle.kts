kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-auth"))
        }
    }
    jvmTest {
        dependencies {
            api("org.apache.directory.server:apacheds-server-integ:2.0.0-M24")
            api("org.apache.directory.server:apacheds-core-integ:2.0.0-M24")
        }
    }
}
