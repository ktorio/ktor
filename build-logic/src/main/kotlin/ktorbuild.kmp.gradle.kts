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
    id("ktorbuild.codestyle")
}

// atomicfu gradle plugin is not compatible with Android KMP plugin
// see https://github.com/Kotlin/kotlinx-atomicfu/issues/511
if (!project.hasAndroidPlugin()) {
    plugins {
        id("org.jetbrains.kotlinx.atomicfu")
    }
}

kotlin {
    jvmToolchain(KtorBuildExtension.DEFAULT_JDK)
    explicitApi()

    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_2
        languageVersion = KotlinVersion.KOTLIN_2_2
        progressiveMode = languageVersion.map { it >= KotlinVersion.DEFAULT }
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }

    applyHierarchyTemplate(KtorTargets.hierarchyTemplate)
    addTargets(ktorBuild.targets, ktorBuild.isCI.get())
}

val targets = ktorBuild.targets

configureCommon()
if (targets.hasJvm) configureJvm()
if (targets.hasJs) configureJs()
if (targets.hasWasmJs) configureWasmJs()
if (targets.hasWeb) configureWeb()
if (targets.hasAndroidJvm && project.hasAndroidPlugin()) configureAndroidJvm()

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
        .configureEach { withLimitedParallelism("native-tools", maxParallelTasks = 3) }
    // A workaround for KT-77609
    tasks.matching { it::class.simpleName?.startsWith("CInteropCommonizerTask") == true }
        .configureEach { withLimitedParallelism("native-tools", maxParallelTasks = 3) }
}

if (ktorBuild.isCI.get()) configureTestTasksOnCi()
