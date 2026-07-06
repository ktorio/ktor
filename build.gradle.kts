import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.root")
}

logger.lifecycle("Build version: ${project.version}")
logger.lifecycle("Kotlin version: ${libs.versions.kotlin.get()}")

allprojects {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("file:///Users/Nikolay.Lunyak/Documents/Projects/kotlin-worktrees/kotlin-platform-type-commonized-to-different-types/build/repo")
    }

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        println("Project: $name")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.configureEach {
                dependencies {
                    implementation("org.jetbrains.kotlin.commonizer:commonizer-support-library:2.4.255-SNAPSHOT")
                }
            }

            compilerOptions.freeCompilerArgs.add("-Xskip-prerelease-check")
        }
    }
}
