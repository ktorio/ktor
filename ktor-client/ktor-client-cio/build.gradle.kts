/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "CIO backend for ktor http client"

apply<test.server.TestServerPlugin>()

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
                api(project(":ktor-http:ktor-http-cio"))
                api(project(":ktor-shared:ktor-websockets"))
                api(project(":ktor-network:ktor-network-tls"))
            }
        }
        commonTest {
            dependencies {
                api(project(":ktor-client:ktor-client-tests"))
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
                api(project(":ktor-shared:ktor-junit"))
                implementation(libs.mockk)
            }
        }
    }
}
