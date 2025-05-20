/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Common HTMX constants for use in client and server"

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                implementation(libs.consul.api)
                api(projects.ktorUtils)
                api(projects.ktorServiceDiscovery)
            }
        }
    }
}
