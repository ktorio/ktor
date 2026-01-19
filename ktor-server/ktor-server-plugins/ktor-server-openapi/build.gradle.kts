/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    // The minimal JDK version required for Swagger Codegen
    jvmToolchain(11)

    sourceSets {
        jvmMain.dependencies {
            implementation(projects.ktorServerHtmlBuilder)
            implementation(projects.ktorServerRoutingOpenapi)

            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.swagger.codegen)
            implementation(libs.swagger.codegen.generators)
            implementation(libs.swagger.parser)
        }
        jvmTest.dependencies {
            implementation(projects.ktorServerContentNegotiation)
            implementation(projects.ktorSerializationKotlinxJson)
            implementation(projects.ktorOpenapiSchemaReflect)
        }
    }
}
