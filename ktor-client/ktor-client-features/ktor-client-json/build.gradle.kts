/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

description = "Ktor client JSON support"

val ideaActive: Boolean by project.extra

plugins {
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        // This is a workaround for https://youtrack.jetbrains.com/issue/KT-39037
        fun excludingSelf(dependency: Any) = project.dependencies.create(dependency).apply {
            (this as ModuleDependency).exclude(module = project.name)
        }

        val commonTest by getting {
            dependencies {
                api(excludingSelf(project(":ktor-client:ktor-client-features:ktor-client-json:ktor-client-serialization")))
            }
        }
        val jvmTest by getting {
            dependencies {
                api(project(":ktor-client:ktor-client-features:ktor-client-json:ktor-client-gson"))
            }
        }
    }
}
