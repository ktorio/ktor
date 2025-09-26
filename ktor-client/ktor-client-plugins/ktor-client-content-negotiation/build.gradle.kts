/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Ktor client Content Negotiation support"

plugins {
    id("ktorbuild.project.client-plugin")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorSerialization)
        }
    }
}
