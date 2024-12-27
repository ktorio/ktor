/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.createCInterop

description = "Ktor network utilities"

kotlin {
    createCInterop("network", sourceSet = "nix")

    sourceSets {
        jvmAndPosixMain {
            dependencies {
                api(project(":ktor-utils"))
            }
        }

        jvmAndPosixTest {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":ktor-shared:ktor-test-base"))
                implementation(libs.mockk)
            }
        }
    }
}
