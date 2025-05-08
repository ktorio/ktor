/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "HTMX support for the Kotlin HTML DSL"

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.html)
            api(projects.ktorHtmx)
            implementation(projects.ktorUtils)
        }
    }
}

