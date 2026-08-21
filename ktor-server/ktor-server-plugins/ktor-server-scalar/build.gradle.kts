/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.ktorServerHtmlBuilder)
            api(projects.ktorServerRoutingOpenapi)
        }
        jvmTest.dependencies {
            implementation(projects.ktorOpenapiSchemaReflect)
        }
    }
}

