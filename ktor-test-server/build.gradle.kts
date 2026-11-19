/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}

// Should be synced with gradle/gradle-daemon-jvm.properties
kotlin {
    jvmToolchain(21)

    compilerOptions {
        allWarningsAsErrors = true
    }
}

tasks.validatePlugins {
    enableStricterValidation = true
}
