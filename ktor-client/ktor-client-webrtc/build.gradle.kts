/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

description = "Ktor WebRTC client"

plugins {
    id("com.android.library")
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

        androidMain {
            dependencies {
                implementation(libs.stream.webrtc.android)
            }
        }

        androidInstrumentedTest.dependencies {
            implementation(libs.androidx.core)
            implementation(libs.androidx.runner)
            implementation(libs.androidx.rules)
        }

        wasmJs {
            compilerOptions {
                freeCompilerArgs.add("-Xwasm-attach-js-exception")
            }
        }

        val commonTest by getting
        val androidInstrumentedTest by getting {
            dependsOn(commonTest)
        }
    }
}

android {
    namespace = "io.ktor.client.webrtc"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    testOptions {
        targetSdk = 35
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("./AndroidManifest.xml")
        }
    }
}

tasks.named("jsNodeTest") { onlyIf { false } }
tasks.named("wasmJsNodeTest") { onlyIf { false } }
