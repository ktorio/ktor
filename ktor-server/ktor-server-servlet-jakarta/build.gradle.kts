/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    jvmToolchain(11)

    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorServerCore)

            compileOnly(libs.jakarta.servlet)
        }

        jvmTest.dependencies {
            api(projects.ktorServerConfigYaml)
            implementation(libs.mockk)
            implementation(libs.jakarta.servlet)
        }
    }
}
