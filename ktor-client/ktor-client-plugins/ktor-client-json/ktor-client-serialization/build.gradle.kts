/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(libs.kotlinx.serialization.json)
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
        }
    }
}
