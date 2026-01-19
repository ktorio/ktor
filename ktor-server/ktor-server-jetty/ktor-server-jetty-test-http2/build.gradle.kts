/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    id("ktorbuild.project.internal")
}

kotlin {
    sourceSets {
        jvmTest.dependencies {
            implementation(projects.ktorServerTestBase)
            implementation(projects.ktorServerTestSuites)
            implementation(libs.jetty.servlet)
            implementation(projects.ktorServerCore)
            implementation(projects.ktorServerJetty)
        }
    }
}

tasks.named<KotlinJvmTest>("jvmTest") {
    systemProperty("enable.http2", "true")
}
