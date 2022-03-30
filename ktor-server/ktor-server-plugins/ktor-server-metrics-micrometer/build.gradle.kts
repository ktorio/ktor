kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(libs.micrometer)
                implementation(project(":ktor-server:ktor-server-host-common"))
            }
        }
    }
}
