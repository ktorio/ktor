/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(libs.freemarker)
        }
        jvmTest.dependencies {
            implementation(projects.ktorServerStatusPages)
            implementation(projects.ktorServerCompression)
            implementation(projects.ktorServerConditionalHeaders)
            implementation(projects.ktorServerContentNegotiation)
        }
    }
}
