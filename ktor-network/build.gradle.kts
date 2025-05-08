/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.createCInterop

description = "Ktor network utilities"

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    createCInterop("network", sourceSet = "nix")
    createCInterop("un", sourceSet = "androidNative")
    createCInterop("un", sourceSet = "darwin")
    createCInterop("afunix", sourceSet = "windows")

    sourceSets {
        commonMain.dependencies {
            api(projects.ktorUtils)
        }

        commonTest.dependencies {
            api(projects.ktorTestDispatcher)
        }

        jvmTest.dependencies {
            implementation(projects.ktorTestBase)
            implementation(libs.mockk)
        }
    }
}
