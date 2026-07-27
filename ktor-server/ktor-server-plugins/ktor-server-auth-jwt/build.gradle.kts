/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    compilerOptions {
        // -Xcontext-parameters requires Kotlin 2.2.0 or newer
        freeCompilerArgs.add("-Xcontext-parameters")
    }
    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorServerAuth)
            api(libs.java.jwt)
            api(libs.jwks.rsa)
        }
        jvmTest.dependencies {
            implementation(libs.mockk)
        }
    }
}
