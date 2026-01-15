/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    id("ktorbuild.project.internal")
}

kotlin {
    // The minimal JVM version required for Jetty 12+
    jvmToolchain(17)

    sourceSets {
        jvmTest.dependencies {
            implementation(projects.ktorServerTestBase)
            implementation(projects.ktorServerTestSuites)
            implementation(libs.jetty.servlet.jakarta)
            implementation(projects.ktorServerCore)
            implementation(projects.ktorServerJettyJakarta)
        }
    }
}

tasks.named<KotlinJvmTest>("jvmTest") {
    systemProperty("enable.http2", "true")
}
