/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("test-server")
}

kotlin {
    sourceSets {
        darwinMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
            }
        }
        darwinTest {
            dependencies {
                api(project(":ktor-client:ktor-client-tests"))
                api(project(":ktor-client:ktor-client-plugins:ktor-client-logging"))
                api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
            }
        }
    }
}
