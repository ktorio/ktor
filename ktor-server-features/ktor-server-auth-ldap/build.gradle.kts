kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-server-features:ktor-server-auth"))
        }
    }
    val jvmTest by getting {
        dependencies {
            api("org.apache.directory.server:apacheds-server-integ:2.0.0-M24")
            api("org.apache.directory.server:apacheds-core-integ:2.0.0-M24")
        }
    }
}
