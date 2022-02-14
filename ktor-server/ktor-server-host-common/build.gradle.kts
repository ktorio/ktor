description = ""

val logback_version: String by extra

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-http:ktor-http-cio"))
            api(project(":ktor-shared:ktor-websockets"))
        }
    }

    jvmTest {
        dependencies {
            implementation(project(":ktor-server:ktor-server-test-host"))
            implementation(project(":ktor-server:ktor-server-test-suites"))
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            api("ch.qos.logback:logback-classic:$logback_version")
        }
    }
}
