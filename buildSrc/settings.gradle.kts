/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

pluginManagement {
    includeBuild("../gradle-settings-conventions")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
    id("conventions-dependency-resolution-management")
}

dependencyResolutionManagement {
    // Additional repositories for buildSrc dependencies
    @Suppress("UnstableApiUsage")
    repositories {
        gradlePluginPortal()

        exclusiveContent {
            forRepository {
                maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap") { name = "KtorEAP" }
            }
            filter { includeGroup("io.ktor") }
        }
    }
}

rootProject.name = "buildSrc"
