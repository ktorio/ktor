/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                implementation(project(":ktor-server:ktor-server-plugins:ktor-server-html-builder"))

                implementation("io.swagger.codegen.v3:swagger-codegen:3.0.35")
                implementation("io.swagger.codegen.v3:swagger-codegen-generators:1.0.35")
                implementation("io.swagger.parser.v3:swagger-parser:2.1.1")
            }
        }
    }
}
