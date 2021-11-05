kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":ktor-io"))
            }
        }
        val commonTest by getting {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }
    }
}
