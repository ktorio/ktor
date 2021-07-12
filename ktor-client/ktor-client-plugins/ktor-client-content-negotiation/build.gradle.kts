/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Ktor client Content Negotiation support"

plugins {
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-shared:ktor-shared-serialization"))
            }
        }
    }
}
