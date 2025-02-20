/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Apache backend for ktor http client"

plugins {
    id("test-server")
}

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
            api(libs.apache.client5)
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
        }
    }
}
