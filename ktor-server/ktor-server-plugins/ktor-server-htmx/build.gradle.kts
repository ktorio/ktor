/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":ktor-shared:ktor-htmx"))
            implementation(project(":ktor-utils"))
        }
        commonTest.dependencies {
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-html-builder"))
            implementation(project(":ktor-shared:ktor-htmx:ktor-htmx-html"))
        }
    }
}
