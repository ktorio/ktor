/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    id("ktorbuild.project.library")
    id("test-server")
}

val isTestJdk21OrHigher = ktorBuild.jvmTestToolchain.get().canCompileOrRun(JavaLanguageVersion.of(21))

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorClientCore)
            // Used to compile against Android SDK without the android plugin
            compileOnly(libs.robolectric.android)
        }
        jvmTest.dependencies {
            implementation(projects.ktorClientTests)
            implementation(projects.ktorNetworkTls)
            implementation(projects.ktorNetworkTlsCertificates)
            compileOnly(libs.robolectric.android)
            compileOnly(libs.robolectric.sandbox)

            if (isTestJdk21OrHigher) {
                runtimeOnly(libs.robolectric.android)
                runtimeOnly(libs.robolectric.sandbox)
            }
        }
    }
}

// pass JVM option to enlarge built-in HttpUrlConnection pool
// to avoid failures due to lack of local socket ports
tasks.named<KotlinJvmTest>("jvmTest") {
    jvmArgs("-Dhttp.maxConnections=32")
    if (isTestJdk21OrHigher) {
        jvmArgs(
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        )
    }
}
