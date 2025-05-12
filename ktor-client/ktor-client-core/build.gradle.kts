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
            api(projects.ktorHttp)
            api(projects.ktorHttpCio)
            api(projects.ktorEvents)
            api(projects.ktorWebsocketSerialization)
            api(projects.ktorSse)
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
            api(projects.ktorTestDispatcher)
            api(projects.ktorClientMock)
            api(projects.ktorServerTestHost)
        }
    }
}
