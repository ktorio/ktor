/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.client-plugin")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            compileOnly(libs.slf4j.simple)
            api(libs.kotlinx.coroutines.slf4j)
        }
        commonTest.dependencies {
            api(projects.ktorClientMock)
            api(projects.ktorClientContentNegotiation)
        }
        jvmTest.dependencies {
            api(projects.ktorSerializationJackson)
            api(projects.ktorClientEncoding)
        }
    }
}
