/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

pluginManagement {
    // Add repositories required for build-settings-logic
    repositories {
        gradlePluginPortal()

        // Should be in sync with ktorsettings.kotlin-user-project
        val kotlinRepoUrl = providers.gradleProperty("kotlin_repo_url").orNull
        val kotlin_version: String? by settings
        val repoUrl = kotlinRepoUrl
        val kotlinVersion = kotlin_version
        if (!repoUrl.isNullOrEmpty() && !kotlinVersion.isNullOrEmpty()) {
            exclusiveContent {
                forRepository {
                    maven(repoUrl)
                }
                filter {
                    includeVersionByRegex("org\\.jetbrains\\.kotlin.*", ".*", kotlinVersion)
                }
            }
        }

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
    }
}

rootProject.name = "build-logic"
