/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.client-plugin")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorClientJson)

            api(libs.jackson.databind)
            api(libs.jackson.module.kotlin)
        }
        jvmTest.dependencies {
            api(projects.ktorClientCio)
            api(projects.ktorSerializationGson)
        }
    }
}
