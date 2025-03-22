/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.internal.ktorBuild

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
        commonTest.dependencies {
            implementation(project(":ktor-client:ktor-client-tests"))
        }

        if (ktorBuild.targets.hasJvm) {
            jvmTest.dependencies {
                runtimeOnly(project(":ktor-client:ktor-client-okhttp"))
                runtimeOnly(project(":ktor-client:ktor-client-apache"))
                runtimeOnly(project(":ktor-client:ktor-client-cio"))
                runtimeOnly(project(":ktor-client:ktor-client-android"))
                runtimeOnly(project(":ktor-client:ktor-client-java"))
            }
        }

        if (ktorBuild.targets.hasJs) {
            jsTest.dependencies {
                runtimeOnly(project(":ktor-client:ktor-client-js"))
            }
        }
    }
}
