/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Common extensions for testing Ktor"

plugins {
    id("ktorbuild.project.internal")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlin.test)
            api(projects.ktorUtils)
            api(projects.ktorTestDispatcher)
        }

        jvmMain.dependencies {
            api(libs.kotlin.test.junit5)
            api(libs.junit)
            api(libs.kotlinx.coroutines.debug)
        }
    }
}
