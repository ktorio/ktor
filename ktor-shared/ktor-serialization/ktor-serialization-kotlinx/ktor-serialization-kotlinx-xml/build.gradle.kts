/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

val xmlutil_version: String by extra
val logback_version: String by extra

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx"))
            api("io.github.pdvrieze.xmlutil:serialization:$xmlutil_version")
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation:ktor-client-content-negotiation-tests")) // ktlint-disable max-line-length
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx:ktor-serialization-kotlinx-tests"))

            api("ch.qos.logback:logback-classic:$logback_version")
        }
    }
}
