/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    // Jackson 3.0 requires >= JDK 17
    jvmToolchain(17)

    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorSerialization)
            api(libs.jackson3.databind)
            api(libs.jackson3.module.kotlin)
        }
        jvmTest.dependencies {
            implementation(projects.ktorServerTestHost)
            implementation(projects.ktorClientTests)
            implementation(projects.ktorClientContentNegotiationTests)
            implementation(projects.ktorSerializationTests)

            implementation(libs.logback.classic)
            implementation(libs.jackson3.dataformat.smile)
        }
    }
}
