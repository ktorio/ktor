/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Server-sent events API for client and server"

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorUtils)
        }
    }
}
