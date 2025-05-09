/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.internal")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(kotlin("test-annotations-common"))
            api(projects.ktorSerializationKotlinx)
            api(projects.ktorClientTests)
        }
        jvmMain.dependencies {
            api(projects.ktorSerializationTests)

            api(libs.logback.classic)
        }
    }
}
