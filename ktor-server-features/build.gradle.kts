subprojects {
    kotlin {
        sourceSets {
            val commonMain by getting {
                dependencies {
                    api(project(":ktor-server:ktor-server"))
                }
            }
            val commonTest by getting {
                dependencies {
                    api(project(":ktor-server:ktor-server-test-host"))
                }
            }
        }
    }
}
