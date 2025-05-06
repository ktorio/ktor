/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Ktor http client"

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-http-cio"))
            api(project(":ktor-events"))
            api(project(":ktor-websocket-serialization"))
            api(project(":ktor-sse"))
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.slf4j)
        }

        jsMain.dependencies {
            api(npm("ws", libs.versions.ws.get()))
        }

        wasmJsMain.dependencies {
            api(npm("ws", libs.versions.ws.get()))
        }

        commonTest.dependencies {
            api(project(":ktor-test-dispatcher"))
            api(project(":ktor-client-mock"))
            api(project(":ktor-server-test-host"))
        }
    }
}
