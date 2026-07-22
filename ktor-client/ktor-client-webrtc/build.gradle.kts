/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.disableNativeCompileConfigurationCache
import ktorbuild.targets.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

description = "Ktor WebRTC Client"

plugins {
    id("ktorbuild.optional.android-library")
    id("ktorbuild.optional.cocoapods")
    id("kotlinx-serialization")
    id("ktorbuild.project.library")
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

    optionalAndroid {
        namespace = "io.ktor.client.webrtc"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        packaging {
            // Both 'net.java.dev.jna:jna-platform' and 'net.java.dev.jna:jna' (5.9.0) provide these licenses,
            // so we have to filter them out to resolve the conflict
            resources.excludes.add("META-INF/AL2.0")
            resources.excludes.add("META-INF/LGPL2.1")
        }
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

        jvmMain.dependencies {
            api(libs.webrtc.java)

            // Gradle has issues resolving a native library with classifier, so we add it manually
            val webRtcJavaNativeLib = libs.webrtc.java.get().toString() + ":" + resolveWebRtcJavaClassifier()
            implementation(webRtcJavaNativeLib)
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

// Exclude JUnit 5 from Android device tests
configurations.named { it.startsWith("androidDeviceTest") }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module(libs.kotlin.test.junit5))
            .using(module(libs.kotlin.test.junit))
            .because("Junit 5 is not supported for Android device tests")
    }

    exclude(group = "org.junit.jupiter")
    exclude(group = "org.junit.platform")
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    if (name.contains("Test", ignoreCase = true)) {
        compilerOptions {
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }
}
