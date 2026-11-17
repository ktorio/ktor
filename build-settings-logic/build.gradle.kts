/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.develocity)
    implementation(libs.develocity.commonCustomUserData)
}

// Should be synced with gradle/gradle-daemon-jvm.properties
kotlin {
    jvmToolchain(21)

    compilerOptions {
        // Kotlin user project builds should never pass -Werror, see ktorsettings.kotlin-user-project
        allWarningsAsErrors = providers.gradleProperty("kotlin_version").map { false }.orElse(true)
    }
}

tasks.validatePlugins {
    enableStricterValidation = true
}
