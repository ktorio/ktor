/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Ktor client plugins"

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
            }
        }
    }
}

subprojects {
    kotlin.sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
            }
        }

        jvmTest {
            dependencies {
                runtimeOnly(project(":ktor-client:ktor-client-okhttp"))
                runtimeOnly(project(":ktor-client:ktor-client-apache"))
                runtimeOnly(project(":ktor-client:ktor-client-cio"))
                runtimeOnly(project(":ktor-client:ktor-client-android"))
                runtimeOnly(project(":ktor-client:ktor-client-okhttp"))
                try {
                    runtimeOnly(project(":ktor-client:ktor-client-java"))
                } catch (_: UnknownProjectException) {
                }
            }
        }

        findByName("jsTest")?.dependencies {
            api(project(":ktor-client:ktor-client-js"))
        }

        commonTest {
            dependencies {
                api(project(":ktor-client:ktor-client-tests"))
            }
        }
    }
}
