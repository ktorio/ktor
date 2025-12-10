/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
description = "Endpoint documentation API for Ktor"

plugins {
    id("ktorbuild.project.server-plugin")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorOpenapiSchema)
            compileOnly(projects.ktorServerAuth)
        }
        commonTest.dependencies {
            implementation(projects.ktorServerTestHost)
            implementation(projects.ktorServerAuth)
            implementation(projects.ktorServerContentNegotiation)
            implementation(projects.ktorSerializationKotlinxJson)
        }
        jvmMain.dependencies {
            compileOnly(projects.ktorServerAuthJwt)
        }
        jvmTest.dependencies {
            implementation(projects.ktorServerAuthJwt)
            implementation(projects.ktorServerSessions)
        }
    }
}
