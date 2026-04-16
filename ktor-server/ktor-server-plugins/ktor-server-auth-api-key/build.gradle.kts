/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorServerAuth)
        }
        commonTest.dependencies {
            implementation(projects.ktorServerContentNegotiation)
            implementation(projects.ktorClientContentNegotiation)
            implementation(projects.ktorSerializationKotlinxJson)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
