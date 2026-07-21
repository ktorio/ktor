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
            api(projects.ktorHttp)
            api(projects.ktorIo)
        }

        commonTest.dependencies {
            implementation(projects.ktorTestBase)
        }

        jvmMain.dependencies {
            api(projects.ktorNetwork)
        }
    }
}
