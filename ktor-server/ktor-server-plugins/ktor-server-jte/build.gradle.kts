/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    // The minimal JDK version required for jte 3.0+
    jvmToolchain(17)

    sourceSets {
        jvmMain.dependencies {
            api(libs.jte)
        }
        jvmTest.dependencies {
            api(projects.ktorServerStatusPages)
            api(projects.ktorServerCompression)
            api(projects.ktorServerConditionalHeaders)
            api(libs.jte.kotlin)
            implementation(projects.ktorServerContentNegotiation)
        }
    }
}
