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
            api(projects.ktorClientCore)
            // Used to compile against Android SDK without the android plugin
            compileOnly(libs.robolectric.android)
        }
        jvmTest.dependencies {
            api(projects.ktorClientTests)
            api(projects.ktorNetworkTls)
            api(projects.ktorNetworkTlsCertificates)
            implementation(libs.robolectric.android)
        }
    }
}

// pass JVM option to enlarge built-in HttpUrlConnection pool
// to avoid failures due to lack of local socket ports
tasks.named<KotlinJvmTest>("jvmTest") {
    jvmArgs("-Dhttp.maxConnections=32")
}
