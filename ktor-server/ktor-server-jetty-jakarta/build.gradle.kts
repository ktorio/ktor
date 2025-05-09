/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    // The minimal JVM version required for Jetty 10+
    jvmToolchain(11)

    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorServerCore)
            api(projects.ktorServerServletJakarta)
            api(libs.jetty.server.jakarta)
            api(libs.jetty.servlets.jakarta)
            api(libs.jetty.alpn.server.jakarta)
            api(libs.jetty.alpn.java.server.jakarta)
            api(libs.jetty.alpn.openjdk8.server)
            api(libs.jetty.http2.server.jakarta)
        }
        jvmTest.dependencies {
            api(libs.kotlin.test.junit5)
            api(projects.ktorServerCore)
            api(projects.ktorServerTestBase)
            api(projects.ktorServerTestSuites)

            api(libs.jetty.servlet.jakarta)
        }
    }
}
