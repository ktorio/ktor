description = ""
kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(libs.dropwizard.core)
                api(libs.dropwizard.jvm)
            }
        }
    }
}
