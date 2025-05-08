/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorServerCore)
            api(projects.ktorServerServlet)
            api(libs.jetty.server)
            api(libs.jetty.servlets)
            api(libs.jetty.alpn.server)
            api(libs.jetty.alpn.java.server)
            api(libs.jetty.alpn.openjdk8.server)
            api(libs.jetty.http2.server)
        }
        jvmTest.dependencies {
            api(projects.ktorServerCore)
            api(projects.ktorServerTestBase)
            api(projects.ktorServerTestSuites)

            api(libs.jetty.servlet)
        }
    }
}
