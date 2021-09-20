/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Common tests for client-json"

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-plugins:ktor-client-serialization:ktor-client-serialization-json"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-serialization:ktor-client-serialization-cbor"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation:ktor-client-content-negotiation-tests"))

        }
    }
}
