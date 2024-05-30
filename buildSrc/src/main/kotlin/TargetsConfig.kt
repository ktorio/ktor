/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("UNUSED_VARIABLE")

import org.gradle.api.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import java.io.*

val Project.files: Array<File> get() = project.projectDir.listFiles() ?: emptyArray()
val Project.hasCommon: Boolean get() = files.any { it.name == "common" }
val Project.hasJvmAndNix: Boolean get() = hasCommon || files.any { it.name == "jvmAndNix" }
val Project.hasPosix: Boolean get() = hasCommon || files.any { it.name == "posix" }
val Project.hasDesktop: Boolean get() = hasPosix || files.any { it.name == "desktop" }
val Project.hasNix: Boolean get() = hasPosix || hasJvmAndNix || files.any { it.name == "nix" }
val Project.hasLinux: Boolean get() = hasNix || files.any { it.name == "linux" }
val Project.hasDarwin: Boolean get() = hasNix || files.any { it.name == "darwin" }
val Project.hasWindows: Boolean get() = hasPosix || files.any { it.name == "windows" }
val Project.hasJsAndWasmShared: Boolean get() = files.any { it.name == "jsAndWasmShared" }
val Project.hasJs: Boolean get() = hasCommon || files.any { it.name == "js" } || hasJsAndWasmShared
val Project.hasWasm: Boolean get() = hasCommon || files.any { it.name == "wasmJs" } || hasJsAndWasmShared
val Project.hasJvm: Boolean get() = hasCommon || hasJvmAndNix || files.any { it.name == "jvm" }
val Project.hasNative: Boolean get() =
    hasCommon || hasNix || hasPosix || hasLinux || hasDarwin || hasDesktop || hasWindows

fun Project.configureTargets() {
    val coroutinesVersion = rootProject.versionCatalog.findVersion("coroutines-version").get().requiredVersion
    configureCommon()
    if (hasJvm) configureJvm()

    kotlin {
        if (hasJs) {
            js(IR) {
                nodejs()
                browser()
            }

            configureJs()
        }

        if (hasWasm) {
            @OptIn(ExperimentalWasmDsl::class)
            wasmJs {
                nodejs()
                browser()
            }

            configureWasm()
        }

        if (hasPosix || hasLinux || hasDarwin || hasWindows) extra.set("hasNative", true)

        if (hasPosix) { posixTargets() }
        if (hasDesktop) { desktopTargets() }
        if (hasNix) { nixTargets() }
        if (hasLinux) { linuxTargets() }
        if (hasDarwin) { darwinTargets() }
        if (hasWindows) { windowsTargets() }

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        applyHierarchyTemplate {
            common {
                group("nix") {
                    group("darwin") {
                        group("macos") { withMacos() }
                        group("ios") { withIos() }
                        group("tvos") { withTvos() }
                        group("watchos") { withWatchos() }
                    }
                    group("linux") { withLinux() }
                }

                withJvm()

                group("jsAndWasmShared") {
                    withJs()
                    withWasm()
                }

                group("posix") {
                    group("nix")
                    group("windows") { withMingw() }
                    group("desktop") {
                        group("macos")
                        group("linux")
                        group("windows")
                    }
                }

                group("jvmAndNix") {
                    group("nix")
                    withJvm()
                }
            }
        }

        sourceSets {
            commonTest {
                dependencies {
                    implementation(kotlin("test"))
                }
            }

            if (hasNative) {
                tasks.findByName("linkDebugTestLinuxX64")?.onlyIf { HOST_NAME == "linux" }
                tasks.findByName("linkDebugTestLinuxArm64")?.onlyIf { HOST_NAME == "linux" }
                tasks.findByName("linkDebugTestMingwX64")?.onlyIf { HOST_NAME == "windows" }
            }
        }
    }

    if (hasJsAndWasmShared) {
        tasks.configureEach {
            if (name == "compileJsAndWasmSharedMainKotlinMetadata") {
                enabled = false
            }
        }
    }
}
