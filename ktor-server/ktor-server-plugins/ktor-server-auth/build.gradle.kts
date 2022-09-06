/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
            }
        }
        jvmMain {
            dependencies {
                api(project(":ktor-server:ktor-server-plugins:ktor-server-sessions"))
                api("com.googlecode.json-simple:json-simple:1.1.1") {
                    isTransitive = false
                }
            }
        }
        commonTest {
            dependencies {
                api(project(":ktor-client:ktor-client-cio"))
                api(project(":ktor-client:ktor-client-mock"))
                api(project(":ktor-server:ktor-server-test-host"))
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-server:ktor-server-plugins:ktor-server-content-negotiation"))
                api(project(":ktor-shared:ktor-serialization:ktor-serialization-jackson"))
            }
        }
    }
}
