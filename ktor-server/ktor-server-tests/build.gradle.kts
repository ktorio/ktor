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
            implementation(projects.ktorServer)
            implementation(projects.ktorServerRateLimit)
            implementation(projects.ktorServerTestHost)
        }
        jvmTest.dependencies {
            implementation(libs.jansi)
            implementation(projects.ktorClientEncoding)
            implementation(projects.ktorServerCompressionZstd)
            implementation(libs.zstd.jni)
            implementation(projects.ktorServerSse)
        }
    }
}
