/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.client-plugin")
    id("test-server")
}

kotlin {
    sourceSets {
        jvmTest.dependencies {
            api(projects.ktorServerTestHost)
        }
    }
}
