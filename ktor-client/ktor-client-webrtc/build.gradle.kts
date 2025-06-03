/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

description = "Ktor WebRTC client"

plugins {
    id("kotlinx-serialization")
    id("ktorbuild.project.library")
}

kotlin {
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            api(project(":ktor-io"))
            api(project(":ktor-utils"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":ktor-test-dispatcher"))
        }

        wasmJs {
            compilerOptions {
                freeCompilerArgs.add("-Xwasm-attach-js-exception")
            }
        }
    }
}
