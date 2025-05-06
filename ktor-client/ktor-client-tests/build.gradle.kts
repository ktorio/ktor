/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Common tests for client"

plugins {
    id("ktorbuild.project.internal")
    id("kotlinx-serialization")
    id("test-server")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":ktor-client-test-base"))
            api(project(":ktor-client-mock"))
        }
        commonTest.dependencies {
            api(project(":ktor-client-json"))
            api(project(":ktor-client-serialization"))
            api(project(":ktor-client-logging"))
            api(project(":ktor-client-auth"))
            api(project(":ktor-client-encoding"))
            api(project(":ktor-client-content-negotiation"))
            api(project(":ktor-client-json"))
            api(project(":ktor-client-serialization"))
            api(project(":ktor-serialization-kotlinx"))
            api(project(":ktor-serialization-kotlinx-json"))
        }
        jvmMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(project(":ktor-network-tls-certificates"))
            api(project(":ktor-server"))
            api(project(":ktor-server-cio"))
            api(project(":ktor-server-netty"))
            api(project(":ktor-server-auth"))
            api(project(":ktor-server-websockets"))
            api(project(":ktor-serialization-kotlinx"))
            api(libs.logback.classic)
        }

        jvmTest.dependencies {
            api(project(":ktor-client-apache"))
            api(project(":ktor-client-apache5"))
            runtimeOnly(project(":ktor-client-android"))
            runtimeOnly(project(":ktor-client-okhttp"))
            runtimeOnly(project(":ktor-client-java"))
            implementation(project(":ktor-client-logging"))
            implementation(libs.kotlinx.coroutines.slf4j)
            implementation(libs.junit)
        }

        commonTest.dependencies {
            api(project(":ktor-client-cio"))
        }

        jsTest.dependencies {
            api(project(":ktor-client-js"))
        }

        desktopTest.dependencies {
            api(project(":ktor-client-curl"))
        }

        darwinTest.dependencies {
                api(project(":ktor-client-darwin"))
                api(project(":ktor-client-darwin-legacy"))
        }

        windowsTest.dependencies {
                api(project(":ktor-client-winhttp"))
        }
    }
}
