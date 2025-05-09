/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.internal")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.ktorServerForwardedHeader)
            implementation(projects.ktorServerAutoHeadResponse)
            implementation(projects.ktorServerStatusPages)
            implementation(projects.ktorServerHsts)
            implementation(projects.ktorServerWebsockets)
            api(projects.ktorServerTestBase)
        }

        jvmMain.dependencies {
            implementation(projects.ktorServerCompression)
            implementation(projects.ktorServerPartialContent)
            implementation(projects.ktorServerConditionalHeaders)
            implementation(projects.ktorServerDefaultHeaders)
            implementation(projects.ktorServerRequestValidation)
        }
    }
}
