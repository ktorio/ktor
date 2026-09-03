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

            api(projects.ktorClientApache5)
            api(projects.ktorNetworkTlsCertificates)
            api(projects.ktorServerCallLogging)

            api(libs.logback.classic)

            // Http3TestClient only: compileOnly so the HTTP/3 stack stays off the test classpath of
            // engines that don't support it. Consumers of Http3TestClient bring Netty themselves.
            compileOnly(libs.netty.codec.http3)
        }
    }
}
