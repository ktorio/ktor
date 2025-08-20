/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.*
import gobley.gradle.Variant
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

description = "Ktor WebRTC Engine based on the WebRTC.rs and Gobley"

plugins {
    id("dev.gobley.rust") version "0.3.2"
    id("dev.gobley.cargo") version "0.3.2"
    id("dev.gobley.uniffi") version "0.3.2"
    id("com.android.library")
    id("ktorbuild.project.library")
    kotlin("plugin.atomicfu") version libs.versions.kotlin.get()
}

uniffi {
    generateFromLibrary {
        namespace = "ktor_client_webrtc"
        // variant = gobley.gradle.Variant.Release is not supported yet for macOS on arm
    }
}

cargo {
    // Configure build variants
    jvmVariant = Variant.Debug
    jvmPublishingVariant = Variant.Release
    nativeVariant = Variant.Release

    builds.jvm {
        // Build Rust library only for the host platform
        embedRustLibrary = (GobleyHost.current.rustTarget == rustTarget)
    }
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            api(projects.ktorClientWebrtc)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.datetime)
            implementation(projects.ktorTestDispatcher)
        }
    }
}

// disable cross-compilation
tasks.named { it == "cinteropRustLinuxArm64" }.configureEach {
    onlyIf { GobleyHost.Platform.Linux.isCurrent }
}
tasks.named { it == "cinteropRustLinuxX64" }.configureEach {
    onlyIf { GobleyHost.Platform.Linux.isCurrent }
}

// Gobley's Android Native target depends on Android Gradle Plugin (not Android Gradle KMP Plugin) to use ndk toolchain
android {
    namespace = "io.ktor.client.webrtc.rs"
    compileSdk = 36

    defaultConfig {
        consumerProguardFiles("proguard-rules.pro")
        ndk.abiFilters.add("arm64-v8a")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
