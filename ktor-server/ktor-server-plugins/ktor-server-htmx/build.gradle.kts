/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorHtmx)
            implementation(projects.ktorUtils)
        }
        commonTest.dependencies {
            implementation(projects.ktorServerHtmlBuilder)
            implementation(projects.ktorHtmxHtml)
        }
    }
}
