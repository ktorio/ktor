kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api("org.freemarker:freemarker:[2.3.20, 2.4)")
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server-features:ktor-server-status-pages"))
            api(project(":ktor-server-features:ktor-server-compression"))
            api(project(":ktor-server-features:ktor-server-conditional-headers"))
        }
    }
}
