/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Ktor server GraphQL plugin"

plugins {
    id("ktorbuild.project.server-plugin")
    id("kotlinx-serialization")
}

kotlin {
    jvmToolchain(11)

    sourceSets {
        jvmMain.dependencies {
            api(libs.graphql.java)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
