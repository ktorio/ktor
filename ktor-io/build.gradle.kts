/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.createCInterop

plugins {
    id("ktorbuild.project.library")
    alias(libs.plugins.kover)
}

kotlin {
    createCInterop("mutex", sourceSet = "posix")

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.io.core)
        }
        commonTest.dependencies {
            api(projects.ktorTestDispatcher)
        }
    }
}
