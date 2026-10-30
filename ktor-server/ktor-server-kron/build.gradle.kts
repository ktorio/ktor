/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Ktor server engine on kron: io_uring reactors, HTTP/1.1 parsed by Glyph11"

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        all {
            languageSettings.optIn("kotlin.concurrent.atomics.ExperimentalAtomicApi")
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }
        linuxMain.dependencies {
            api(projects.ktorServerCore)
            api(libs.kron.core)
            implementation(libs.kron.glyph11)
        }
        linuxTest.dependencies {
            implementation(projects.ktorServerTestSuites)
            implementation(projects.ktorServerTestBase)
            implementation(projects.ktorServerWebsockets)
        }
    }
}
