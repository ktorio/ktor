/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    // The minimal JVM version required for Tomcat 10
    jvmToolchain(11)

    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorServerCore)
            api(projects.ktorServerServletJakarta)
            api(libs.tomcat.catalina.jakarta)
            api(libs.tomcat.embed.core.jakarta)
        }
        jvmTest.dependencies {
            api(projects.ktorServerTestBase)
            api(projects.ktorServerTestSuites)
            api(projects.ktorServerCore)
        }
    }
}
