/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("test-server")
}

kotlin {
    sourceSets {
        commonTest {
            dependencies {
                api(libs.kotlinx.coroutines.test)
            }
        }

        wasmJsTest {
            dependencies {
                api(project(":ktor-webrtc-client:ktor-webrtc-client-js"))
            }
        }
    }
}
