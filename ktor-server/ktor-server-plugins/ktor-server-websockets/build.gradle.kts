/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorWebsockets)
            api(projects.ktorWebsocketSerialization)
        }

        commonTest.dependencies {
            api(projects.ktorServerContentNegotiation)
            api(projects.ktorClientWebsockets)
        }

        jvmTest.dependencies {
            implementation(projects.ktorTestBase)
        }
    }
}
