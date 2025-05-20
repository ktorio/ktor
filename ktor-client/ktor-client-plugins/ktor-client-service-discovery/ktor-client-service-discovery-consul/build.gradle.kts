/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.client-plugin")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(libs.consul.api)
            api(projects.ktorServiceDiscoveryConsul)
            api(projects.ktorClientServiceDiscovery)
        }
        jvmTest.dependencies {
            api(projects.ktorServiceDiscoveryConsul)
            api(projects.ktorClientServiceDiscovery)
            api(projects.ktorClientMock)
        }
    }
}
