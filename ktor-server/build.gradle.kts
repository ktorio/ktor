/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Wrapper for ktor-server-core and base plugins"

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(project(":ktor-server-call-logging"))
            api(project(":ktor-server-default-headers"))
            api(project(":ktor-server-compression"))
        }
        commonMain.dependencies {
            api(project(":ktor-server-core"))
            api(project(":ktor-server-auto-head-response"))
            api(project(":ktor-server-caching-headers"))
            api(project(":ktor-server-conditional-headers"))
            api(project(":ktor-server-content-negotiation"))
            api(project(":ktor-server-call-id"))
            api(project(":ktor-server-cors"))
            api(project(":ktor-server-csrf"))
            api(project(":ktor-server-data-conversion"))
            api(project(":ktor-server-double-receive"))
            api(project(":ktor-server-forwarded-header"))
            api(project(":ktor-server-hsts"))
            api(project(":ktor-server-http-redirect"))
            api(project(":ktor-server-partial-content"))
            api(project(":ktor-server-status-pages"))
            api(project(":ktor-server-method-override"))
            api(project(":ktor-server-sessions"))
        }
    }
}
