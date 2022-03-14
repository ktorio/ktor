description = ""

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(libs.webjars.locator)
            }
        }
        val jvmTest by getting {
            dependencies {
                api(libs.webjars.jquery)
            }
        }
    }
}
