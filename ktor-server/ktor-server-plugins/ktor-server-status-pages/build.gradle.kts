/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""
val logback_version: String by extra

kotlin {
    sourceSets {
        val jvmAndNixTest by getting {
            dependencies {
                api(project(":ktor-server:ktor-server-test-host"))
            }
        }

        jvmTest {
            dependencies {
                api("ch.qos.logback:logback-classic:$logback_version")
            }
        }
    }
}
