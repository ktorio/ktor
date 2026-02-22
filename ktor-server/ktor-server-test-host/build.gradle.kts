/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Test utilities for testing Ktor server applications without starting a real server"

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    js {
        compilerOptions {
            target = "es2015"
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.ktorClientCio)
            api(projects.ktorServerCore)
            api(projects.ktorClientCore)
            api(projects.ktorTestDispatcher)
        }

        jvmMain.dependencies {
            api(projects.ktorNetworkTls)

            api(projects.ktorClientApache5)
            api(projects.ktorNetworkTlsCertificates)
            api(projects.ktorServerCallLogging)

            // Not ideal, but prevents an additional artifact, and this is usually just included for testing,
            // so shouldn't increase the size of the final artifact.
            api(projects.ktorServerWebsockets)

            implementation(libs.kotlinx.coroutines.debug)
        }

        jvmTest.dependencies {
            implementation(projects.ktorServerConfigYaml)
            implementation(libs.kotlin.test)
            implementation(libs.junit)
        }
    }
}
