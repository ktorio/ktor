/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:OptIn(ExperimentalWasmDsl::class)

import ktorbuild.disableNativeCompileConfigurationCache
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

description = "Ktor WebRTC Client"

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("kotlinx-serialization")
    id("ktorbuild.project.library")
    kotlin("native.cocoapods")
}

fun resolveWebRtcJavaClassifier(): String {
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val platform = when {
        osName.contains("mac") || osName.contains("darwin") -> "macos"
        osName.contains("windows") -> "windows"
        osName.contains("linux") -> "linux"
        else -> error("Unsupported OS: $osName")
    }
    val arch = when {
        osArch.contains("aarch64") || osArch.contains("arm64") -> "aarch64"
        osArch.contains("amd64") || osArch.contains("x86_64") || osArch.contains("x64") -> "x86_64"
        osArch.contains("x86") -> "x86"
        else -> error("Unsupported architecture: $osArch")
    }
    return "$platform-$arch"
}

kotlin {
    jvmToolchain(17)

    androidLibrary {
        namespace = "io.ktor.client.webrtc"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
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

        jvmMain.dependencies {
            implementation(libs.webrtc.java)

            // Gradle has issues resolving a native library with classifier, so we add it manually
            val webRtcJavaNativeLib = libs.webrtc.java.get().toString() + ":" + resolveWebRtcJavaClassifier()
            implementation(webRtcJavaNativeLib)
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

        cocoapods {
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
            }

            framework {
                baseName = "KtorWebRTC"
            }

            noPodspec()
        }
    }

    disableNativeCompileConfigurationCache()
}
