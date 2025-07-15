/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

description = "Ktor WebRTC Client"

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
            // using `atomicfu` as a library, but not a plugin
            // because its plugin is not compatible with Android KMP plugin
            implementation(libs.kotlinx.atomicfu)
            api(project(":ktor-io"))
            api(libs.kotlinx.serialization.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":ktor-test-dispatcher"))
        }

        jsAndWasmSharedMain.dependencies {
            implementation(kotlinWrappers.browser)
        }

        wasmJs {
            compilerOptions {
                freeCompilerArgs.add("-Xwasm-attach-js-exception")
            }
        }

        androidMain.dependencies {
            implementation(libs.stream.webrtc.android)
        }
    }
}
