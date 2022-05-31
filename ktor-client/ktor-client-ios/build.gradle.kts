kotlin {
    fastTarget()

    sourceSets {
        darwinMain {
            dependencies {
                api(project(":ktor-client:ktor-client-darwin"))
            }
        }
    }
}
