/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.gradle.api.*
import org.gradle.api.tasks.testing.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.konan.target.*
import org.jetbrains.kotlin.gradle.tasks.*
import java.io.*

private val Project.files: Array<File> get() = project.projectDir.listFiles() ?: emptyArray()
val Project.hasCommon: Boolean get() = files.any { it.name == "common" }
val Project.hasJvmAndPosix: Boolean get() = hasCommon || files.any { it.name == "jvmAndPosix" }
val Project.hasJvmAndNix: Boolean get() = hasCommon || files.any { it.name == "jvmAndNix" }
val Project.hasPosix: Boolean get() = hasCommon || hasJvmAndPosix || files.any { it.name == "posix" }
val Project.hasDesktop: Boolean get() = hasPosix || files.any { it.name == "desktop" }
val Project.hasNix: Boolean get() = hasPosix || hasJvmAndNix || files.any { it.name == "nix" }
val Project.hasLinux: Boolean get() = hasNix || files.any { it.name == "linux" }
val Project.hasDarwin: Boolean get() = hasNix || files.any { it.name == "darwin" }
val Project.hasAndroidNative: Boolean get() = hasPosix || files.any { it.name == "androidNative" }
val Project.hasWindows: Boolean get() = hasPosix || files.any { it.name == "windows" }
val Project.hasJsAndWasmShared: Boolean get() = files.any { it.name == "jsAndWasmShared" }
val Project.hasJs: Boolean get() = hasCommon || files.any { it.name == "js" } || hasJsAndWasmShared
val Project.hasWasm: Boolean get() = hasCommon || files.any { it.name == "wasmJs" } || hasJsAndWasmShared
val Project.hasJvm: Boolean get() = hasCommon || hasJvmAndNix || hasJvmAndPosix || files.any { it.name == "jvm" }

val Project.hasExplicitNative: Boolean
    get() = hasNix || hasPosix || hasLinux || hasAndroidNative || hasDarwin || hasDesktop || hasWindows
val Project.hasNative: Boolean
    get() = hasCommon || hasExplicitNative

fun Project.configureTargets() {
    kotlin {
        configureCommon()

        if (hasJvm) configureJvm()

        if (hasJs) configureJs()
        if (hasWasm) configureWasm()

        if (hasPosix) posixTargets()
        if (hasNix) nixTargets()
        if (hasDarwin) darwinTargets()
        if (hasLinux) linuxTargets()
        if (hasAndroidNative) androidNativeTargets()
        if (hasDesktop) desktopTargets()
        if (hasWindows) windowsTargets()

        applyHierarchyTemplate(hierarchyTemplate)
    }

    if (hasExplicitNative) extra["hasNative"] = true
    if (hasNative) {
        tasks.maybeNamed("linkDebugTestLinuxX64") { onlyIf { HOST_NAME == "linux" } }
        tasks.maybeNamed("linkDebugTestLinuxArm64") { onlyIf { HOST_NAME == "linux" } }
        tasks.maybeNamed("linkDebugTestMingwX64") { onlyIf { HOST_NAME == "windows" } }
    }

    if (hasJsAndWasmShared) {
        tasks.configureEach {
            if (name == "compileJsAndWasmSharedMainKotlinMetadata") {
                enabled = false
            }
        }
    }
}

private val hierarchyTemplate = KotlinHierarchyTemplate {
    withSourceSetTree(KotlinSourceSetTree.main, KotlinSourceSetTree.test)

    common {
        group("posix") {
            group("windows") { withMingw() }

            group("nix") {
                group("linux") { withLinux() }

                group("darwin") {
                    group("ios") { withIos() }
                    group("tvos") { withTvos() }
                    group("watchos") { withWatchos() }
                    group("macos") { withMacos() }
                }

                group("androidNative") {
                    group("androidNative64") {
                        withAndroidNativeX64()
                        withAndroidNativeArm64()
                    }

                    group("androidNative32") {
                        withAndroidNativeX86()
                        withAndroidNativeArm32Fixed()
                    }
                }
            }
        }

        group("jsAndWasmShared") {
            withJs()
            withWasmJs()
        }

        group("jvmAndPosix") {
            withJvm()
            group("posix")
        }

        group("desktop") {
            group("linux")
            group("windows")
            group("macos")
        }

        group("nonJvm") {
            group("posix")
            group("jsAndWasmShared")
        }
    }
}

/**
 * By default, all targets are enabled. To disable specific target,
 * disable the corresponding flag in `gradle.properties` of the target project.
 *
 * Targets that could be disabled:
 * - `target.js.nodeJs`
 * - `target.js.browser`
 * - `target.wasmJs.browser`
 * - `target.androidNative`
 */
internal fun Project.targetIsEnabled(target: String): Boolean {
    return findProperty("target.$target") != "false"
}

/**
 * Original `withAndroidNativeArm32` has a bug and matches to `X86` actually.
 * TODO: Remove after the bug is fixed
 *  https://youtrack.jetbrains.com/issue/KT-71866/
 */
private fun KotlinHierarchyBuilder.withAndroidNativeArm32Fixed() = withCompilations {
    val target = it.target
    target is KotlinNativeTarget && target.konanTarget == KonanTarget.ANDROID_ARM32
}
