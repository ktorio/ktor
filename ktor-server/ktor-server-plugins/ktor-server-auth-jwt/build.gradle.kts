/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":ktor-server:ktor-server-plugins:ktor-server-auth"))
                api(libs.java.jwt)
                api(libs.jwks.rsa)
            }
        }
        jvmTest {
            dependencies {
                api(libs.mockk)
            }
        }
    }
}

