/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.*
import org.gradle.api.tasks.testing.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.plugin.*
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
val Project.hasWindows: Boolean get() = hasPosix || files.any { it.name == "windows" }
val Project.hasJsAndWasmShared: Boolean get() = files.any { it.name == "jsAndWasmShared" }
val Project.hasJs: Boolean get() = hasCommon || files.any { it.name == "js" } || hasJsAndWasmShared
val Project.hasWasm: Boolean get() = hasCommon || files.any { it.name == "wasmJs" } || hasJsAndWasmShared
val Project.hasJvm: Boolean get() = hasCommon || hasJvmAndNix || hasJvmAndPosix || files.any { it.name == "jvm" }

val Project.hasExplicitNative: Boolean
    get() = hasNix || hasPosix || hasLinux || hasDarwin || hasDesktop || hasWindows
val Project.hasNative: Boolean
    get() = hasCommon || hasExplicitNative

fun Project.configureTargets() {
    configureCommon()
    if (hasJvm) configureJvm()
    kotlin {
        configureCommon()

        if (hasJvm) configureJvm()

        if (hasJs) configureJs()
        if (hasWasm) configureWasm()

        if (hasPosix) posixTargets()
        if (hasNix) nixTargets()
        if (hasDarwin) darwinTargets()
        if (hasLinux) linuxTargets()
        if (hasDesktop) desktopTargets()
        if (hasWindows) windowsTargets()

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
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

    // Don't fail build on the CI:
    // 1. To distinct builds failed because of failed tests and because of compilation errors or anything else.
    //    TeamCity parses test results to define build status, so the build won't be green.
    // 2. To run as many tests as possible while keeping fail-fast behavior locally.
    if (CI) tasks.withType<AbstractTestTask>().configureEach {
        ignoreFailures = true
        if (this is KotlinTest) ignoreRunFailures = true
    }
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
private val hierarchyTemplate = KotlinHierarchyTemplate {
    withSourceSetTree(KotlinSourceSetTree.main, KotlinSourceSetTree.test)

    common {
        group("posix") {
            group("windows") { withMingw() }

            group("nix") {
                group("linux") { withLinux() }

                group("darwin") {
                    withApple()

                    group("ios") { withIos() }
                    group("tvos") { withTvos() }
                    group("watchos") { withWatchos() }
                    group("macos") { withMacos() }
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

        group("jvmAndNix") {
            withJvm()
            group("nix")
        }

        group("desktop") {
            group("linux")
            group("windows")
            group("macos")
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
 */
internal fun Project.targetIsEnabled(target: String): Boolean {
    return findProperty("target.$target") != "false"
}
