
kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(libs.jackson.databind)
                api(libs.jackson.module.kotlin)
            }
        }
    }
}
