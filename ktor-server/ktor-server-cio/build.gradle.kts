description = ""

kotlin.sourceSets {
    val jvmAndNixMain by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-host-common"))
            api(project(":ktor-http:ktor-http-cio"))
            api(project(":ktor-shared:ktor-websockets"))
            api(project(":ktor-network"))
        }
    }
    val jvmAndNixTest by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-client:ktor-client-cio"))
            api(project(":ktor-server:ktor-server-test-suites"))
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
        }
    }
}
