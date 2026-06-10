/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorServerCore)
            api(projects.ktorClientCore)
            api(projects.ktorClientContentNegotiation)
            api(projects.ktorSerializationKotlinxJson)
            api(libs.kotlinx.serialization.json)
            api(projects.ktorClientContentNegotiation)
            api(projects.ktorSerializationKotlinxJson)
        }
        jvmTest.dependencies {
            implementation(projects.ktorServerTestHost)
            implementation(projects.ktorServerContentNegotiation)
        }
    }
}
