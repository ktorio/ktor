/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.internal")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":ktor-server-test-host"))
            api(project(":ktor-test-base"))
        }

        jvmMain.dependencies {
            api(project(":ktor-network-tls"))

            api(project(":ktor-client-apache"))
            api(project(":ktor-network-tls-certificates"))
            api(project(":ktor-server-call-logging"))

            api(libs.logback.classic)
        }
    }
}
