description = ""

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(libs.webjars.locator)
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))
                api(project(":ktor-server:ktor-server-plugins:ktor-server-caching-headers"))
                api(libs.webjars.jquery)
            }
        }
    }
}
