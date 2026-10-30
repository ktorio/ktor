/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Micrometer metrics for the Ktor Server Rate Limit plugin"

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorServerMetricsMicrometer)
            api(projects.ktorServerRateLimit)
        }
    }
}
