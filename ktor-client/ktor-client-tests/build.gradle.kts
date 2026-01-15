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
            api(projects.ktorClientTestBase)
            api(projects.ktorClientMock)
        }
        commonTest.dependencies {
            implementation(projects.ktorClientCio)
            implementation(projects.ktorClientLogging)
            implementation(projects.ktorClientAuth)
            implementation(projects.ktorClientEncoding)
            implementation(projects.ktorClientContentNegotiation)
            implementation(projects.ktorClientJson)
            implementation(projects.ktorClientSerialization)
            implementation(projects.ktorSerializationKotlinx)
            implementation(projects.ktorSerializationKotlinxJson)
        }
        jvmMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(projects.ktorNetworkTlsCertificates)
            api(projects.ktorServer)
            api(projects.ktorServerCio)
            api(projects.ktorServerNetty)
            api(projects.ktorServerAuth)
            api(projects.ktorServerWebsockets)
            api(projects.ktorSerializationKotlinx)
            api(libs.logback.classic)
        }

        jvmTest.dependencies {
            implementation(projects.ktorClientApache5)
            runtimeOnly(projects.ktorClientAndroid)
            runtimeOnly(projects.ktorClientOkhttp)
            runtimeOnly(projects.ktorClientJava)
            implementation(libs.kotlinx.coroutines.slf4j)
            implementation(libs.junit)
        }

        jsTest.dependencies {
            implementation(projects.ktorClientJs)
        }

        desktopTest.dependencies {
            implementation(projects.ktorClientCurl)
        }

        darwinTest.dependencies {
            implementation(projects.ktorClientDarwin)
            implementation(projects.ktorClientDarwinLegacy)
        }

        windowsTest.dependencies {
            implementation(projects.ktorClientWinhttp)
        }
    }
}
