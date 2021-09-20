/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Ktor client JSON Content Negotiation via kotlinx.serialization support"
val serialization_version = extra["serialization_version"]

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets.commonMain {
    dependencies {
        api(project(":ktor-client:ktor-client-plugins:ktor-client-serialization"))
        api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
    }
}
