/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.createCInterop

plugins {
    id("ktorbuild.project.library")
    id("kotlinx-serialization")
    id("test-server")
}

kotlin {
    createCInterop("winhttp", sourceSet = "windows")

    sourceSets {
        windowsMain.dependencies {
            api(projects.ktorClientCore)
            api(projects.ktorHttpCio)
        }
        windowsTest.dependencies {
            implementation(projects.ktorClientTestBase)
            api(projects.ktorClientLogging)
            api(projects.ktorClientJson)
        }
    }
}
