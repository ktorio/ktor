description = ""

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-host-common"))
            api(project(":ktor-http:ktor-http-cio"))
            api(project(":ktor-network"))
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-server:ktor-server-test-suites"))
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-client:ktor-client-cio"))
        }
    }
}
