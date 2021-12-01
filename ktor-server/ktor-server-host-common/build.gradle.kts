description = ""

kotlin.sourceSets {
    val jvmAndNixMain by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-http:ktor-http-cio"))
            api(project(":ktor-shared:ktor-websockets"))
        }
    }

    val jvmTest by getting {
        dependencies {
            implementation(project(":ktor-server:ktor-server-test-host"))
            implementation(project(":ktor-server:ktor-server-test-suites"))
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
        }
    }
}
