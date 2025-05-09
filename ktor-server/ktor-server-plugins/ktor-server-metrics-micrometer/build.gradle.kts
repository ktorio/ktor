/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(libs.micrometer)
            implementation(projects.ktorServerCore)
        }
        jvmTest.dependencies {
            implementation(projects.ktorServerMetrics)
            implementation(projects.ktorServerAuth)
        }
    }
}
