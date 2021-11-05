kotlin {
    sourceSets {
        val commonTest by getting {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }
    }
}
