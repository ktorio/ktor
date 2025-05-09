/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(libs.thymeleaf)
        }
        jvmTest.dependencies {
            api(projects.ktorServerConditionalHeaders)
            api(projects.ktorServerCompression)
            implementation(projects.ktorServerContentNegotiation)
        }
    }
}
