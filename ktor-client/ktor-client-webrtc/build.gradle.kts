/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:OptIn(ExperimentalWasmDsl::class, ExperimentalKotlinGradlePluginApi::class)

import ktorbuild.disableNativeCompileConfigurationCache
import ktorbuild.targets.*
import org.jetbrains.kotlin.gradle.*

description = "Ktor WebRTC Client"

plugins {
    id("ktorbuild.optional.android-library")
    id("ktorbuild.optional.cocoapods")
    id("kotlinx-serialization")
    id("ktorbuild.project.library")
}

kotlin {
    jvmToolchain(17)

    optionalAndroidLibrary {
        namespace = "io.ktor.client.webrtc"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    optionalCocoapods {
        version = project.version.toString()
        summary = "Ktor WebRTC Client"
        homepage = "https://github.com/ktorio/ktor"
        source = "https://github.com/ktorio/ktor"
        authors = "JetBrains"
        license = "https://www.apache.org/licenses/LICENSE-2.0"
        ios.deploymentTarget = libs.versions.ios.deploymentTarget.get()

        pod("WebRTC-SDK") {
            version = libs.versions.ios.webrtc.sdk.get()
            moduleName = "WebRTC"
            packageName = "WebRTC"
            extraOpts += listOf("-compiler-option", "-fmodules")
            extraOpts += listOf("-compiler-option", "-DTARGET_OS_VISION=0")
        }

        framework {
            baseName = "KtorWebRTC"
            isStatic = true
        }

        noPodspec()
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
            api(kotlinWrappers.browser)
        }

        wasmJs {
            compilerOptions {
                freeCompilerArgs.add("-Xwasm-attach-js-exception")
            }
        }

        optional.androidMain.dependencies {
            api(libs.stream.webrtc.android)
        }
    }

    disableNativeCompileConfigurationCache()
}
