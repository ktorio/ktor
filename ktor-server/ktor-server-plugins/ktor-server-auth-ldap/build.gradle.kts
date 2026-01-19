/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorServerAuth)
        }
        jvmTest.dependencies {
            implementation(libs.apacheds.server)
            implementation(libs.apacheds.core)
        }
    }
}
