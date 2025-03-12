/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(libs.dropwizard.core)
                api(libs.dropwizard.jvm)
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
                api(project(":ktor-server:ktor-server-plugins:ktor-server-cors"))
                api(project(":ktor-shared:ktor-test-base"))
            }
        }
    }
}
