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
                api(libs.freemarker)
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
                api(project(":ktor-server:ktor-server-plugins:ktor-server-compression"))
                api(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))
                implementation(project(":ktor-server:ktor-server-plugins:ktor-server-content-negotiation"))
            }
        }
    }
}
