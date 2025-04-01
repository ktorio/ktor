/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

kotlin {
    sourceSets {
        commonTest {
            dependencies {
                api(project(":ktor-webrtc-client:ktor-webrtc-client-core"))
                api(libs.kotlinx.coroutines.test)
            }
        }

        jsAndWasmSharedTest {
            dependencies {
                api(project(":ktor-webrtc-client:ktor-webrtc-client-js"))
            }
        }
    }
}
