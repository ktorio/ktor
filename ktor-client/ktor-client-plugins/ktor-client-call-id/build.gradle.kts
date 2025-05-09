/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Ktor client CallId support"

plugins {
    id("ktorbuild.project.client-plugin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorCallId)
        }
        commonTest.dependencies {
            api(projects.ktorServerTestHost)
            api(projects.ktorServerCallId)
        }
    }
}

// tests need server, so can't be run in browser
tasks.named("jsBrowserTest") { onlyIf { false } }
tasks.named("wasmJsBrowserTest") { onlyIf { false } }
