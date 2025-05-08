/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.internal")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorClientCore)
            api(projects.ktorTestBase)
        }
    }
}
