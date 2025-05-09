/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorClientCio)
            api(projects.ktorServerCore)
            api(projects.ktorClientCore)
            api(projects.ktorTestDispatcher)
        }

        jvmMain.dependencies {
            api(projects.ktorNetworkTls)

            api(projects.ktorClientApache)
            api(projects.ktorNetworkTlsCertificates)
            api(projects.ktorServerCallLogging)

            // Not ideal, but prevents an additional artifact, and this is usually just included for testing,
            // so shouldn"t increase the size of the final artifact.
            api(projects.ktorServerWebsockets)

            api(libs.kotlin.test)
            api(libs.junit)
            implementation(libs.kotlinx.coroutines.debug)
        }

        jvmTest.dependencies {
            api(projects.ktorServerConfigYaml)
            api(libs.kotlin.test)
        }
    }
}
