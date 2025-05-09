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
            compileOnly(libs.javax.servlet)
        }

        jvmTest.dependencies {
            api(projects.ktorServerConfigYaml)
            implementation(libs.mockk)
            implementation(libs.javax.servlet)
        }
    }
}
