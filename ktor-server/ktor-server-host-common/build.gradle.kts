description = "This module is deprecated. All the contents are moved to `ktor-server-core`."

kotlin {
    sourceSets {
        jvmAndPosixMain {
            dependencies {
                api(project(":ktor-server:ktor-server-core"))
            }
        }
    }
}
