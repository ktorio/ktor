apply<test.server.TestServerPlugin>()

kotlin {

    sourceSets {
        darwinMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
                implementation("org.jetbrains.kotlinx:atomicfu:0.23.1")
            }
        }
        darwinTest {
            dependencies {
                api(project(":ktor-client:ktor-client-tests"))
                api(project(":ktor-client:ktor-client-plugins:ktor-client-logging"))
                api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
            }
        }
    }
}
