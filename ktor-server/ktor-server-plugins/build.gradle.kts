val logback = libs.logback.classic

subprojects {
    kotlin {
        sourceSets {
            commonMain {
                dependencies {
                    api(project(":ktor-server:ktor-server-core"))
                }
            }
            commonTest {
                dependencies {
                    api(project(":ktor-server:ktor-server-test-base"))
                }
            }

            jvmTest {
                dependencies {
                    api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))

                    api(logback)
                }
            }
        }
    }
}
