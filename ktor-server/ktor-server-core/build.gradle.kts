/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.createCInterop

description = ""

plugins {
    id("ktorbuild.project.library")
    id("kotlinx-serialization")
}

kotlin {
    createCInterop("host_common", sourceSet = "nix")

    sourceSets {
        commonMain.dependencies {
            api(projects.ktorUtils)
            api(projects.ktorHttp)
            api(projects.ktorSerialization)
            api(projects.ktorEvents)
            api(projects.ktorHttpCio)
            api(projects.ktorWebsockets)

            api(libs.kotlin.reflect)
        }

        jvmMain.dependencies {
            api(libs.typesafe.config)
            implementation(libs.jansi)
        }

        commonTest.dependencies {
            api(projects.ktorServerTestHost)
        }

        jvmTest.dependencies {
            implementation(projects.ktorServerConfigYaml)
            implementation(projects.ktorServerTestBase)
            implementation(projects.ktorServerTestSuites)

            implementation(libs.mockk)
        }
    }
}
