
kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                implementation(project(":ktor-server:ktor-server-host-common"))
            }
        }
    }
}
