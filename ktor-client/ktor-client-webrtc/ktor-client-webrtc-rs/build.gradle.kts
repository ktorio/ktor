/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.jvm

description = "Ktor WebRTC Engine based on the WebRTC.rs and Gobley"

plugins {
    id("dev.gobley.cargo") version "0.3.1"
    id("dev.gobley.uniffi") version "0.3.1"
    kotlin("plugin.atomicfu") version libs.versions.kotlin.get()
    id("ktorbuild.project.library")
}

uniffi {
    generateFromLibrary {
        namespace = "ktor_client_webrtc"
        // variant = gobley.gradle.Variant.Release is not supported yet for macOS on arm
    }
    formatCode = true
}

cargo {
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
            implementation(projects.ktorTestDispatcher)
        }
    }
}
