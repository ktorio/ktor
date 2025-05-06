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
            implementation(project(":ktor-server-forwarded-header"))
            implementation(project(":ktor-server-auto-head-response"))
            implementation(project(":ktor-server-status-pages"))
            implementation(project(":ktor-server-hsts"))
            implementation(project(":ktor-server-websockets"))
            api(project(":ktor-server-test-base"))
        }

        jvmMain.dependencies {
            implementation(project(":ktor-server-compression"))
            implementation(project(":ktor-server-partial-content"))
            implementation(project(":ktor-server-conditional-headers"))
            implementation(project(":ktor-server-default-headers"))
            implementation(project(":ktor-server-request-validation"))
        }
    }
}
