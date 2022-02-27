/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*

fun KotlinMultiplatformExtension.posixTargets(): Set<KotlinNativeTarget> =
    nixTargets() + mingwX64()

fun KotlinMultiplatformExtension.nixTargets(): Set<KotlinNativeTarget> =
    darwinTargets() + linuxX64()

fun KotlinMultiplatformExtension.darwinTargets(): Set<KotlinNativeTarget> = setOf(
    iosX64(),
    iosArm64(),
    iosArm32(),
    iosSimulatorArm64(),

    watchosX86(),
    watchosX64(),
    watchosArm32(),
    watchosArm64(),
    watchosSimulatorArm64(),

    tvosX64(),
    tvosArm64(),
    tvosSimulatorArm64(),

    macosX64(),
    macosArm64()
)


fun KotlinMultiplatformExtension.macosTargets(): Set<KotlinNativeTarget> = setOf(
    macosX64(),
    macosArm64()
)

fun KotlinMultiplatformExtension.iosTargets(): Set<KotlinNativeTarget> = setOf(
    iosX64(),
    iosArm64(),
    iosArm32(),
    iosSimulatorArm64(),
)

fun KotlinMultiplatformExtension.watchosTargets(): Set<KotlinNativeTarget> = setOf(
    watchosX86(),
    watchosX64(),
    watchosArm32(),
    watchosArm64(),
    watchosSimulatorArm64(),
)

fun KotlinMultiplatformExtension.tvosTargets(): Set<KotlinNativeTarget> = setOf(
    tvosX64(),
    tvosArm64(),
    tvosSimulatorArm64(),
)

fun KotlinMultiplatformExtension.desktopTargets(): Set<KotlinNativeTarget> = setOf(
    macosX64(),
    macosArm64(),
    linuxX64(),
    mingwX64()
)
