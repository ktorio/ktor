description = "Apache backend for ktor http client"

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
            api(libs.apache.httpasyncclient)
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
        }
    }
}
