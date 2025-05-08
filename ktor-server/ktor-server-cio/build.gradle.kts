/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorServerCore)
            api(projects.ktorHttpCio)
            api(projects.ktorWebsockets)
            api(projects.ktorNetwork)
        }
        commonTest.dependencies {
            api(projects.ktorClientCio)
            api(projects.ktorServerTestSuites)
            api(projects.ktorServerTestBase)
        }
    }
}
