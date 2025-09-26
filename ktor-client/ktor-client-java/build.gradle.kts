/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.library")
    id("test-server")
}

kotlin {
    // Package java.net.http was introduced in Java 11
    jvmToolchain(11)

    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorClientCore)
            implementation(libs.kotlinx.coroutines.jdk8)
        }
        jvmTest.dependencies {
            api(projects.ktorClientTests)
        }
    }
}
