/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    // Keep it in sync with libs.versions.toml
    id("com.gradle.develocity") version "3.19.2"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))

            // Make it possible to override Kotlin version
            // See ktorsettings.kotlin-user-project for more details
            val kotlinVersion = providers.gradleProperty("kotlin_version").orNull
            if (kotlinVersion != null) version("kotlin", kotlinVersion)
        }
    }

    repositories {
        gradlePluginPortal()

        // Should be in sync with ktorsettings.kotlin-user-project
        val kotlinRepoUrl = providers.gradleProperty("kotlin_repo_url").orNull
        if (kotlinRepoUrl != null) maven(kotlinRepoUrl) { name = "KotlinDev" }
    }
}

rootProject.name = "build-settings-logic"

// region Build Cache Settings
develocity {
    // Should be in sync with ktorbuild.develocity.settings.gradle.kts
    server = "https://ge.jetbrains.com"
}

val isCIRun = providers.environmentVariable("TEAMCITY_VERSION").isPresent
buildCache {
    if (isCIRun) {
        local {
            isEnabled = false
        }
    }

    remote(develocity.buildCache) {
        isPush = isCIRun
        isEnabled = true
    }
}
// endregion
