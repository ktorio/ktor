/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(libs.pebble)
        }
        jvmTest.dependencies {
            api(project(":ktor-server-conditional-headers"))
            api(project(":ktor-server-compression"))
            implementation(project(":ktor-server-content-negotiation"))
        }
    }
}
