/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorSerialization)
            api(libs.gson)
        }
        jvmTest.dependencies {
            api(projects.ktorServerTestHost)
            api(projects.ktorClientTests)
            api(projects.ktorClientContentNegotiationTests)
            api(projects.ktorSerializationTests)

            api(libs.logback.classic)
        }
    }
}
