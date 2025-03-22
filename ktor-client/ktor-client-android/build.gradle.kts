/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    id("ktorbuild.project.library")
    id("test-server")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(project(":ktor-client:ktor-client-core"))
            // Used to compile against Android SDK without the android plugin
            compileOnly(libs.robolectric.android)
        }
        jvmTest.dependencies {
            api(project(":ktor-client:ktor-client-tests"))
            api(project(":ktor-network:ktor-network-tls"))
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
            implementation(libs.robolectric.android)
        }
    }
}

// pass JVM option to enlarge built-in HttpUrlConnection pool
// to avoid failures due to lack of local socket ports
tasks.named<KotlinJvmTest>("jvmTest") {
    jvmArgs("-Dhttp.maxConnections=32")
}
