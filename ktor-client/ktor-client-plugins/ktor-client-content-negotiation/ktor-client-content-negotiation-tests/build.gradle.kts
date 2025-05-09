/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Common tests for client content negotiation"

plugins {
    id("ktorbuild.project.internal")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(libs.kotlin.test.junit5)
            api(projects.ktorClientContentNegotiation)
            api(projects.ktorServerCio)
            api(projects.ktorClientCio)
            api(projects.ktorClientTests)
            api(projects.ktorServerTestHost)
            api(libs.jackson.annotations)
            api(libs.logback.classic)
        }
    }
}
