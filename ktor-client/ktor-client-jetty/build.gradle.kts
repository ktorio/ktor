/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Jetty based client engine"

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorClientCore)

            api(libs.jetty.http2.client)
            api(libs.jetty.alpn.openjdk8.client)
            api(libs.jetty.alpn.java.client)
        }
        commonTest.dependencies {
            api(projects.ktorClientTests)
        }
    }
}
