/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.internal")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlin.test)
                api(libs.kotlinx.coroutines.test)
                api(project(":ktor-test-dispatcher"))
                api(project(":ktor-webrtc-client:ktor-webrtc-client-core"))
            }
        }

        jsAndWasmSharedTest {
            dependencies {
                api(project(":ktor-webrtc-client:ktor-webrtc-client-js"))
            }
        }
    }
}

tasks.named("jsNodeTest") { onlyIf { false } }
tasks.named("wasmJsNodeTest") { onlyIf { false } }
