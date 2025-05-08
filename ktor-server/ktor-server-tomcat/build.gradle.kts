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
            api(libs.tomcat.catalina)
            api(libs.tomcat.embed.core)
        }
        jvmTest.dependencies {
            api(projects.ktorServerTestBase)
            api(projects.ktorServerTestSuites)
            api(projects.ktorServerCore)
        }
    }
}
