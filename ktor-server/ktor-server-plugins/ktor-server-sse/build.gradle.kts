/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Server-sent events (SSE) support"

plugins {
    id("ktorbuild.project.server-plugin")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorSse)
        }
        commonTest.dependencies {
            api(projects.ktorSerializationKotlinx)
            api(projects.ktorSerializationKotlinxJson)
        }
    }
}
