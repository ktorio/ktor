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
            api(libs.jackson.databind)
            api(libs.jackson.module.kotlin)
        }
        jvmTest.dependencies {
            implementation(projects.ktorServerTestHost)
            implementation(projects.ktorClientTests)
            implementation(projects.ktorClientContentNegotiationTests)
            implementation(projects.ktorSerializationTests)

            implementation(libs.logback.classic)
            implementation(libs.jackson.dataformat.smile)
        }
    }
}
