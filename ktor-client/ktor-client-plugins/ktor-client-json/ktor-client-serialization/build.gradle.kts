/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


val serialization_version = extra["serialization_version"]

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
        }
    }
}
