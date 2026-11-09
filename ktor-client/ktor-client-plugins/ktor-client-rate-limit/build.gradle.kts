/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Rate Limit plugin for Ktor Client"

plugins {
    id("ktorbuild.project.client-plugin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorRateLimit)
        }
    }
}
