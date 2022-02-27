/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

description = "Ktor client JSON support"

plugins {
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        // This is a workaround for https://youtrack.jetbrains.com/issue/KT-39037
        fun excludingSelf(dependency: Any) = project.dependencies.create(dependency).apply {
            (this as ModuleDependency).exclude(module = project.name)
        }

        commonTest {
            dependencies {
                api(excludingSelf(project(":ktor-client:ktor-client-plugins:ktor-client-json:ktor-client-serialization"))) // ktlint-disable max-line-length
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-client:ktor-client-plugins:ktor-client-json:ktor-client-gson"))
            }
        }
    }
}

useJdkVersionForJvmTests(11)
