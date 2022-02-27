kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                // 1.1.3 is the latest version that works on older Android so we are unable to upgrade
                api("io.micrometer:micrometer-core:1.8.3")
                implementation(project(":ktor-server:ktor-server-host-common"))
            }
        }
    }
}
