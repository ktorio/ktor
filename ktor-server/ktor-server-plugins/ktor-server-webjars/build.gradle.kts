description = ""

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api("org.webjars:webjars-locator-core:0.48")
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))
                api("org.webjars:jquery:3.3.1")
            }
        }
    }
}
