/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.internal")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorServerTestHost)
            api(projects.ktorClientContentNegotiationTests)

            api(libs.logback.classic)
        }
    }
}
