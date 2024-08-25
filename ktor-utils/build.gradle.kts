/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("kotlinx-serialization")
}

kotlin {
    createCInterop("threadUtils", nixTargets()) {
        definitionFile = File(projectDir, "nix/interop/threadUtils.def")
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-io"))
                api(libs.kotlinx.serialization.core)
            }
        }
        commonTest {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }
        jvmTest {
            dependencies {
                implementation(project(":ktor-shared:ktor-junit"))
            }
        }
    }
}
