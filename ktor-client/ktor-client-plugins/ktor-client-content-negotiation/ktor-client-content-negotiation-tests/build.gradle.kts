/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Common tests for client content negotiation"

plugins {
    id("kotlinx-serialization")
}

val jackson_version: String by extra
val logback_version: String by extra

kotlin.sourceSets.jvmMain {
    dependencies {
        api(kotlin("test"))
        api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation"))
        api(project(":ktor-server:ktor-server-cio"))
        api(project(":ktor-client:ktor-client-cio"))
        api(project(":ktor-client:ktor-client-tests"))
        api(project(":ktor-server:ktor-server-test-host"))
        api("com.fasterxml.jackson.core:jackson-annotations:$jackson_version")
        api("ch.qos.logback:logback-classic:$logback_version")
    }
}
