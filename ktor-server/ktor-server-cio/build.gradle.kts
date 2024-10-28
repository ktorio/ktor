/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

kotlin.sourceSets {
    jvmAndPosixMain {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-http:ktor-http-cio"))
            api(project(":ktor-shared:ktor-websockets"))
            api(project(":ktor-network"))
        }
    }
    jvmAndPosixTest {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-client:ktor-client-cio"))
            api(project(":ktor-server:ktor-server-test-suites"))
            api(project(":ktor-server:ktor-server-test-base"))
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            api(libs.logback.classic)
        }
    }
}
