/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "ZSTD encoding support"

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.ktorIo)
            implementation(projects.ktorUtils)
        }
        commonTest.dependencies {
            implementation(projects.ktorTestDispatcher)
        }
        jvmMain.dependencies {
            implementation(libs.zstd.jni)
        }
    }
}
