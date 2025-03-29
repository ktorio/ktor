@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

//allprojects {
//    repositories {
//        google()
//        mavenCentral()
//    }
//}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-webrtc-client:ktor-webrtc-client-core"))
            }
        }

        wasmJs {
            browser()
        }

//        androidMain {
//            dependencies {
//                implementation("io.getstream:stream-webrtc-android:1.0.2")
//
//                // Kotlin coroutines
//                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
//                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
//            }
//        }
    }
}
