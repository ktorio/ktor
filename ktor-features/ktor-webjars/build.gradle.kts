description = ""

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api("org.webjars:webjars-locator-core:0.46")
            }
        }
        val jvmTest by getting {
            dependencies {
                api("org.webjars:jquery:3.6.0")
            }
        }
    }
}
