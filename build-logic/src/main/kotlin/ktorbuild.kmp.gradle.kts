/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import ktorbuild.KtorBuildExtension
import ktorbuild.internal.*
import ktorbuild.internal.gradle.maybeNamed
import ktorbuild.targets.*
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

plugins {
    id("ktorbuild.base")
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.atomicfu")
    id("ktorbuild.codestyle")
}

kotlin {
    jvmToolchain(KtorBuildExtension.DEFAULT_JDK)
    explicitApi()

    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_1
        progressiveMode = languageVersion.map { it >= KotlinVersion.DEFAULT }
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }

    applyHierarchyTemplate(KtorTargets.hierarchyTemplate)
    addTargets(ktorBuild.targets)
}

val targets = ktorBuild.targets

configureCommon()
if (targets.hasJvm) configureJvm()
if (targets.hasJs) configureJs()
if (targets.hasWasmJs) configureWasmJs()

if (targets.hasJsOrWasmJs) {
    tasks.configureEach {
        if (name == "compileJsAndWasmSharedMainKotlinMetadata") enabled = false
    }
}

// Run native tests only on matching host.
// There is no need to configure `onlyIf` for Darwin targets as they're configured by KGP.
@Suppress("UnstableApiUsage")
if (targets.hasNative) {
    tasks.maybeNamed("linkDebugTestLinuxX64") {
        val os = ktorBuild.os.get()
        onlyIf("run only on Linux") { os.isLinux }
    }
    tasks.maybeNamed("linkDebugTestLinuxArm64") {
        val os = ktorBuild.os.get()
        onlyIf("run only on Linux") { os.isLinux }
    }
    tasks.maybeNamed("linkDebugTestMingwX64") {
        val os = ktorBuild.os.get()
        onlyIf("run only on Windows") { os.isWindows }
    }

    // A workaround for KT-70915
    tasks.withType<KotlinNativeLink>()
        .configureEach { withLimitedParallelism("native-link", maxParallelTasks = 1) }
    // A workaround for KT-77609
    tasks.matching { it::class.simpleName?.startsWith("CInteropCommonizerTask") == true }
        .configureEach { withLimitedParallelism("cinterop-commonizer", maxParallelTasks = 1) }
}

if (ktorBuild.isCI.get()) configureTestTasksOnCi()
