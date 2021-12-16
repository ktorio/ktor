kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.3.1")
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
