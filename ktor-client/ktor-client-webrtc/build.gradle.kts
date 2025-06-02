/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

description = "Ktor WebRTC client"

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("kotlinx-serialization")
    id("ktorbuild.project.library")
}

kotlin {
    jvmToolchain(17)

    androidLibrary {
        namespace = "io.ktor.client.webrtc"
        compileSdk = 35
        minSdk = 28
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":ktor-io"))
            api(project(":ktor-utils"))
        }

        commonTest.dependencies {
            // using `atomicfu` as a library, but not a plugin
            // because its plugin is not compatible with Android KMP plugin
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlin.test)
            implementation(project(":ktor-test-dispatcher"))
        }

        androidMain.dependencies {
            implementation(libs.stream.webrtc.android)
        }

        wasmJs {
            compilerOptions {
                freeCompilerArgs.add("-Xwasm-attach-js-exception")
            }
        }
    }
}
