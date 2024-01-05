/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                implementation(project(":ktor-server:ktor-server-plugins:ktor-server-html-builder"))
            }
        }
    }
}
