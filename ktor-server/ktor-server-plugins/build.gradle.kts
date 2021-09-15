subprojects {
    kotlin {
        sourceSets {
            val commonMain by getting {
                dependencies {
                    api(project(":ktor-server:ktor-server-core"))
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
