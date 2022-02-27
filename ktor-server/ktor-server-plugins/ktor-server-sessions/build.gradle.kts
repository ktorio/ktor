/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

kotlin {
    sourceSets {
        jvmTest {
            dependencies {
                implementation(project(":ktor-server:ktor-server-netty"))
            }
        }
    }
}
