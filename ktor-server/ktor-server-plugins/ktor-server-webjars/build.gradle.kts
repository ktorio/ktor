description = ""

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api("org.webjars:webjars-locator-core:0.47")
            }
        }
        val jvmTest by getting {
            dependencies {
                api(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))
                api("org.webjars:jquery:3.3.1")
            }
        }
    }
}
