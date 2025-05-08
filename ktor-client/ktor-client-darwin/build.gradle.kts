/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.library")
    id("test-server")
}

kotlin {
    sourceSets {
        darwinMain.dependencies {
            api(projects.ktorClientCore)
            api(projects.ktorNetworkTls)
        }
        darwinTest.dependencies {
            api(projects.ktorClientTests)
            api(projects.ktorClientLogging)
            api(projects.ktorClientJson)
        }
    }
}
