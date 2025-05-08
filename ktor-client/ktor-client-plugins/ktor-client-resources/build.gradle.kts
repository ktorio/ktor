/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Client side Resources feature"

plugins {
    id("ktorbuild.project.client-plugin")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorResources)
            api(libs.kotlinx.serialization.core)
        }
    }
}
