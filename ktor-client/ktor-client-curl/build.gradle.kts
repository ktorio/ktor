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
    createCInterop("libcurl", sourceSet = "desktop") { target ->
        includeDirs(file("desktop/interop/include"))
        extraOpts("-libraryPath", file("desktop/interop/lib/$target"))
    }

    sourceSets {
        desktopMain.dependencies {
            api(projects.ktorClientCore)
            api(projects.ktorHttpCio)
        }
        desktopTest.dependencies {
            implementation(projects.ktorClientTestBase)
            api(projects.ktorClientLogging)
            api(projects.ktorClientJson)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
