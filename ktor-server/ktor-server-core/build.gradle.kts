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
            api(project(":ktor-utils"))
            api(project(":ktor-http"))
            api(project(":ktor-serialization"))
            api(project(":ktor-events"))
            api(project(":ktor-http-cio"))
            api(project(":ktor-websockets"))

            api(libs.kotlin.reflect)
        }

        jvmMain.dependencies {
            api(libs.typesafe.config)
            implementation(libs.jansi)
        }

        commonTest.dependencies {
            api(project(":ktor-server-test-host"))
        }

        jvmTest.dependencies {
            implementation(project(":ktor-server-config-yaml"))
            implementation(project(":ktor-server-test-base"))
            implementation(project(":ktor-server-test-suites"))

            implementation(libs.mockk)
        }
    }
}
