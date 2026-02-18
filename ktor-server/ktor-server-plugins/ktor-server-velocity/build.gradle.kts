/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
}

configurations.all {
    resolutionStrategy {
        force("commons-beanutils:commons-beanutils:1.11.0")
    }
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(libs.velocity)
            api(libs.velocity.tools)
        }
        jvmTest.dependencies {
            implementation(projects.ktorServerConditionalHeaders)
            implementation(projects.ktorServerCompression)
            implementation(projects.ktorServerContentNegotiation)
        }
    }
}
