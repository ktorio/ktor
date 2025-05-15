/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.createCInterop

plugins {
    id("ktorbuild.project.library")
    id("kotlinx-serialization")
}

kotlin {
    createCInterop(
        "threadUtils",
        sourceSet = "nix",
        definitionFilePath = { target ->
            // We create an empty cinterop for androidNative targets
            // to overcome an issue with cinterop Gradle dependencies resolution
            when {
                target.startsWith("androidNative") -> "androidNative/interop/threadUtils.def"
                else -> "nix/interop/threadUtils.def"
            }
        }
    )

    sourceSets {
        commonMain.dependencies {
            api(projects.ktorIo)
            api(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            api(projects.ktorTestDispatcher)
        }
        jvmTest.dependencies {
            implementation(projects.ktorTestBase)
        }
    }
}
