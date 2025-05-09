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
            api(projects.ktorServerCallLogging)
            api(projects.ktorServerDefaultHeaders)
            api(projects.ktorServerCompression)
        }
        commonMain.dependencies {
            api(projects.ktorServerCore)
            api(projects.ktorServerAutoHeadResponse)
            api(projects.ktorServerCachingHeaders)
            api(projects.ktorServerConditionalHeaders)
            api(projects.ktorServerContentNegotiation)
            api(projects.ktorServerCallId)
            api(projects.ktorServerCors)
            api(projects.ktorServerCsrf)
            api(projects.ktorServerDataConversion)
            api(projects.ktorServerDoubleReceive)
            api(projects.ktorServerForwardedHeader)
            api(projects.ktorServerHsts)
            api(projects.ktorServerHttpRedirect)
            api(projects.ktorServerPartialContent)
            api(projects.ktorServerStatusPages)
            api(projects.ktorServerMethodOverride)
            api(projects.ktorServerSessions)
        }
    }
}
