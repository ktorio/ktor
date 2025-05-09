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
            api(projects.ktorServerCore)
            // KTOR-8386 Remove in next major release
            api(libs.yamlkt.serialization)
            implementation(libs.kaml.serialization)
        }
    }
}
