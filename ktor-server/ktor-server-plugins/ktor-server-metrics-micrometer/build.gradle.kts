kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(libs.micrometer)
                implementation(project(":ktor-server:ktor-server-host-common"))
            }
        }
        jvmTest {
            dependencies{
                implementation(project(":ktor-server:ktor-server-plugins:ktor-server-metrics"))
            }
        }
    }
}
