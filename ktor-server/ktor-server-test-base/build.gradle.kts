/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.internal")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorServerTestHost)
            api(projects.ktorTestBase)
        }

        jvmMain.dependencies {
            api(projects.ktorNetworkTls)

            api(projects.ktorClientApache)
            api(projects.ktorNetworkTlsCertificates)
            api(projects.ktorServerCallLogging)

            api(libs.logback.classic)
        }
    }
}
