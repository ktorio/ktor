/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:OptIn(ExperimentalWasmDsl::class, ExperimentalKotlinGradlePluginApi::class)

import ktorbuild.disableNativeCompileConfigurationCache
import ktorbuild.targets.*
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

description = "Ktor WebRTC Client"

plugins {
    id("ktorbuild.optional.android-library")
    id("ktorbuild.optional.cocoapods")
    id("kotlinx-serialization")
    id("ktorbuild.project.library")
}

kotlin {
    jvmToolchain(17)

    optionalAndroid {
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
            api(projects.ktorIo)
            api(libs.kotlinx.serialization.core)
        }

        commonTest.dependencies {
            implementation(projects.ktorTestBase)
        }

        webMain.dependencies {
            api(kotlinWrappers.browser)
        }

        optional.androidMain.dependencies {
            api(libs.stream.webrtc.android)
        }
    }

    disableNativeCompileConfigurationCache()
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    if (name.contains("Test", ignoreCase = true)) {
        compilerOptions {
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }
}
