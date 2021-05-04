kotlin {
    sourceSets {
        val jvmTest by getting {
            dependencies {
                implementation(project(":ktor-server:ktor-server-netty"))
            }
        }
    }
}
