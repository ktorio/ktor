/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

pluginManagement {
    // Add repositories required for build-settings-logic
    repositories {
        gradlePluginPortal()

        // Should be in sync with ktorsettings.kotlin-user-project
        val kotlinRepoUrl = providers.gradleProperty("kotlin_repo_url").orNull
        if (kotlinRepoUrl != null) maven(kotlinRepoUrl) { name = "KotlinDev" }

        maven("https://packages.jetbrains.team/maven/p/kt/dev")
    }

    includeBuild("../build-settings-logic")
}

plugins {
    id("ktorsettings")
}

dependencyResolutionManagement {
    // Additional repositories for build-logic
    @Suppress("UnstableApiUsage")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://packages.jetbrains.team/maven/p/kt/dev")
    }
}

rootProject.name = "build-logic"
