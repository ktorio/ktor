/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

pluginManagement {
    // Add repositories required for build-settings-logic
    repositories {
        google()
        gradlePluginPortal()

        // Should be in sync with ktorsettings.kotlin-user-project
        val kotlinRepoUrl = providers.gradleProperty("kotlin_repo_url").orNull
        if (kotlinRepoUrl != null) maven(kotlinRepoUrl) { name = "KotlinDev" }
    }

    includeBuild("../build-settings-logic")
}

plugins {
    id("ktorsettings")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("ktorLibs") {
            from("io.ktor:ktor-version-catalog:3.3.3-eap-1444")
        }
    }
}

rootProject.name = "ktor-test-server"
