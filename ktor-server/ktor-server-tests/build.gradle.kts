/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.internal")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            api(projects.ktorServer)
            api(projects.ktorServerRateLimit)
            api(projects.ktorServerTestHost)
        }
        jvmTest.dependencies {
            implementation(libs.jansi)
            implementation(projects.ktorClientEncoding)
            api(projects.ktorServerSse)
        }
    }
}
