/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Common tests for client content negotiation"

plugins {
    id("kotlinx-serialization")
}

val jackson_version: String by project.extra

kotlin.sourceSets.jvmMain {
    dependencies {
        api(kotlin("test"))
        api(project(":ktor-client:ktor-client-features:ktor-client-content-negotiation"))
        api(project(":ktor-server:ktor-server-cio"))
        api(project(":ktor-client:ktor-client-cio"))
        api(project(":ktor-client:ktor-client-tests"))
        api("com.fasterxml.jackson.core:jackson-annotations:$jackson_version")
    }
}
