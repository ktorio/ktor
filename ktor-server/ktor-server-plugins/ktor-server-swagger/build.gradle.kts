/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(projects.ktorServerHtmlBuilder)
            api(projects.ktorServerRoutingOpenapi)

            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kaml.serialization)
        }
        jvmTest.dependencies {
            implementation(projects.ktorOpenapiSchemaReflect)
        }
    }
}
