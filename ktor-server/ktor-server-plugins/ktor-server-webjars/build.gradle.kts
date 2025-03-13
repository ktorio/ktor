/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(libs.webjars.locator)
        }
        jvmTest.dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-caching-headers"))
            api(libs.webjars.jquery)
        }
    }
}
