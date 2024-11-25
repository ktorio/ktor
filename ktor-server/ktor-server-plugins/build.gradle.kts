/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

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
                    api(project(":ktor-server:ktor-server-test-host"))
                }
            }

            jvmTest {
                dependencies {
                    api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))

                    // Version catalogs aren't accessible directly inside subprojects block
                    // https://github.com/gradle/gradle/issues/16634#issuecomment-809345790
                    api(rootProject.libs.logback.classic)
                }
            }
        }
    }
}
