description = ""
kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api("io.dropwizard.metrics:metrics-core:4.2.5")
                api("io.dropwizard.metrics:metrics-jvm:4.2.4")
            }
        }
    }
}
