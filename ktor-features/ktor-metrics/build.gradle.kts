description = ""
kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(libs.dropwizard.core)
                api(libs.dropwizard.jvm)
            }
        }
    }
}
