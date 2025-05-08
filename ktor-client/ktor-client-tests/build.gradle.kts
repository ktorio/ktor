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
            api(projects.ktorClientJson)
            api(projects.ktorClientSerialization)
            api(projects.ktorClientLogging)
            api(projects.ktorClientAuth)
            api(projects.ktorClientEncoding)
            api(projects.ktorClientContentNegotiation)
            api(projects.ktorClientJson)
            api(projects.ktorClientSerialization)
            api(projects.ktorSerializationKotlinx)
            api(projects.ktorSerializationKotlinxJson)
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
            api(projects.ktorClientApache)
            api(projects.ktorClientApache5)
            runtimeOnly(projects.ktorClientAndroid)
            runtimeOnly(projects.ktorClientOkhttp)
            runtimeOnly(projects.ktorClientJava)
            implementation(projects.ktorClientLogging)
            implementation(libs.kotlinx.coroutines.slf4j)
            implementation(libs.junit)
        }

        commonTest.dependencies {
            api(projects.ktorClientCio)
        }

        jsTest.dependencies {
            api(projects.ktorClientJs)
        }

        desktopTest.dependencies {
            api(projects.ktorClientCurl)
        }

        darwinTest.dependencies {
                api(projects.ktorClientDarwin)
                api(projects.ktorClientDarwinLegacy)
        }

        windowsTest.dependencies {
                api(projects.ktorClientWinhttp)
        }
    }
}
