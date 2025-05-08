/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.library")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorSerializationKotlinx)
            api(libs.xmlutil.serialization)
        }
        commonTest.dependencies {
            implementation(projects.ktorSerializationKotlinxTests)
        }
        jvmTest.dependencies {
            implementation(projects.ktorClientContentNegotiationTests)
        }
    }
}
