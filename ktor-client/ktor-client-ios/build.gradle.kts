/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        darwinMain.dependencies {
            api(project(":ktor-client:ktor-client-darwin"))
        }
    }
}
