/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(project(":ktor-server:ktor-server-plugins:ktor-server-auth-jwt"))
            }
        }
    }
}
