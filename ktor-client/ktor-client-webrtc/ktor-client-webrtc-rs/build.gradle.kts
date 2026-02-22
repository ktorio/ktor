/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.*
import gobley.gradle.Variant

description = "Ktor WebRTC Engine based on the WebRTC.rs and Gobley"

plugins {
    // Wait until 0.3.8 is released because of https://github.com/gobley/gobley/issues/259
    id("dev.gobley.rust") version "0.3.7"
    id("dev.gobley.cargo") version "0.3.7"
    id("dev.gobley.uniffi") version "0.3.7"
    id("ktorbuild.project.library")
    kotlin("plugin.atomicfu") version libs.versions.kotlin.get()
}

uniffi {
    generateFromLibrary {
        namespace = "ktor_client_webrtc"
        variant = Variant.Release
    }
}

cargo {
    jvmVariant = Variant.Release
    nativeVariant = Variant.Release

    builds.jvm {
        // Build Rust library only for the host platform
        embedRustLibrary = (GobleyHost.current.rustTarget == rustTarget)
    }
}

kotlin {
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

    val overrideKonanProperties = providers.environmentVariable("KTOR_OVERRIDE_KONAN_PROPERTIES").orNull

    if (overrideKonanProperties != null && GobleyHost.Platform.Linux.isCurrent) {
        linuxX64 {
            compilerOptions {
                freeCompilerArgs.addAll("-Xoverride-konan-properties=$overrideKonanProperties")
            }
        }
    }
}

// disable cross-compilation

// Linux
tasks.named { it.startsWith("cargoBuildLinux") || it.startsWith("cinteropRustLinux") }.configureEach {
    onlyIf { GobleyHost.Platform.Linux.isCurrent }
}

// Windows
tasks.named {
    it.startsWith("cargoBuildMingw", ignoreCase = true) || it.startsWith("cinteropRustMingw", ignoreCase = true)
}.configureEach {
    onlyIf { GobleyHost.Platform.Windows.isCurrent }
}

// macOS
tasks.named { it.startsWith("cargoBuildMacOS") || it.startsWith("cinteropRustMacOS") }.configureEach {
    onlyIf { GobleyHost.Platform.MacOS.isCurrent }
}
