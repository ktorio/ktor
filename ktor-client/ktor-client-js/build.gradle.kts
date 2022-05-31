kotlin {
    fastTarget()

    sourceSets {
        jsMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
            }
        }
    }
}
