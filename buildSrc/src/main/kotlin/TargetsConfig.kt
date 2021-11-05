/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("UNUSED_VARIABLE")

import org.gradle.api.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.targets.native.tasks.*

fun Project.configureTargets() {
    val coroutines_version: String by extra
    val kotlin_version: String by extra

    val files = project.projectDir.listFiles() ?: emptyArray()

    val hasCommon = files.any { it.name == "common" }
    val hasJvmAndNix = hasCommon || files.any { it.name == "jvmAndNix" }
    val hasPosix = hasCommon || files.any { it.name == "posix" }
    val hasDesktop = hasPosix || files.any { it.name == "desktop" }
    val hasNix = hasPosix || hasJvmAndNix || files.any { it.name == "nix" }
    val hasDarwin = hasNix || files.any { it.name == "darwin" }
    val hasJs = hasCommon || files.any { it.name == "js" }
    val hasJvm = hasCommon || hasJvmAndNix || files.any { it.name == "jvm" }
    val hasNative = hasCommon || hasNix || hasPosix || hasDarwin || hasDesktop

    kotlin {
        if (hasJvm) {
            jvm()
            configureJvm()
        }

        if (hasJs) {
            js {
                nodejs()
                browser()
            }

            configureJs()
        }

        if (hasPosix || hasDarwin) extra.set("hasNative", true)

        sourceSets {
            if (hasPosix) {
                val posixMain by creating
                val posixTest by creating
            }

            if (hasNix) {
                val nixMain by creating
                val nixTest by creating

                val nix32Main by creating
                val nix32Test by creating

                val nix64Main by creating
                val nix64Test by creating
            }

            if (hasDarwin) {
                val darwinMain by creating
                val darwinTest by creating
            }

            if (hasDesktop) {
                val desktopMain by creating
                val desktopTest by creating
            }

            if (hasCommon) {
                val commonMain by getting {
                    dependencies {
                        api("org.jetbrains.kotlin:kotlin-stdlib-common")
                        api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
                    }
                }

                val commonTest by getting {
                    dependencies {
                        api("org.jetbrains.kotlin:kotlin-test-common:$kotlin_version")
                        api("org.jetbrains.kotlin:kotlin-test-annotations-common:$kotlin_version")
                    }
                }
            }

            if (hasJvmAndNix) {
                val jvmAndNixMain by creating {
                    findByName("commonMain")?.let { dependsOn(it) }
                }

                val jvmAndNixTest by creating {
                    dependencies {
                        api("org.jetbrains.kotlin:kotlin-test-common:$kotlin_version")
                        api("org.jetbrains.kotlin:kotlin-test-annotations-common:$kotlin_version")
                    }
                }
            }

            if (hasJvm) {
                val jvmMain by getting {
                    findByName("jvmAndNixMain")?.let { dependsOn(it) }
                }

                val jvmTest by getting {
                    findByName("jvmAndNixTest")?.let { dependsOn(it) }
                }
            }

            if (hasPosix) {
                val posixMain by getting {
                    findByName("commonMain")?.let { dependsOn(it) }
                }

                val posixTest by getting

                posixTargets().forEach {
                    getByName("${it.name}Main").dependsOn(posixMain)
                    getByName("${it.name}Test").dependsOn(posixTest)
                }
            }

            if (hasNix) {
                val nixMain by getting {
                    findByName("posixMain")?.let { dependsOn(it) }
                    findByName("jvmAndNixMain")?.let { dependsOn(it) }
                }

                val nixTest by getting {
                    findByName("jvmAndNixTest")?.let { dependsOn(it) }
                }

                val nix32Main by getting {
                    dependsOn(nixMain)
                }

                val nix64Main by getting {
                    dependsOn(nixMain)
                }

                val nix32Test by getting
                val nix64Test by getting

                nixTargets().forEach {
                    getByName("${it.name}Main").dependsOn(nixMain)
                    getByName("${it.name}Test").dependsOn(nixTest)
                }

                nix32Targets().forEach {
                    getByName("${it.name}Main").dependsOn(nix32Main)
                    getByName("${it.name}Test").dependsOn(nix32Test)
                }

                nix64Targets().forEach {
                    getByName("${it.name}Main").dependsOn(nix64Main)
                    getByName("${it.name}Test").dependsOn(nix64Test)
                }
            }
            if (hasDarwin) {
                val darwinMain by getting {
                    findByName("nixMain")?.let { dependsOn(it) }
                }

                val darwinTest by getting

                darwinTargets().forEach {
                    getByName("${it.name}Main").dependsOn(darwinMain)
                    getByName("${it.name}Test").dependsOn(darwinTest)
                }
            }
            if (hasDesktop) {
                val desktopMain by getting {
                    findByName("posixMain")?.let { dependsOn(it) }
                }

                val desktopTest by getting

                desktopTargets().forEach {
                    getByName("${it.name}Main").dependsOn(desktopMain)
                    getByName("${it.name}Test").dependsOn(desktopTest)
                }
            }

            if (hasNative) {
                tasks.findByName("linkDebugTestLinuxX64")?.onlyIf { HOST_NAME == "linux" }
                tasks.findByName("linkDebugTestMingwX64")?.onlyIf { HOST_NAME == "windows" }
            }
        }
    }

    tasks.findByName("mingwX64Test")?.apply {
        if (this !is KotlinNativeTest) return@apply
        environment("PATH", "C:\\msys64\\mingw64\\bin;C:\\Tools\\msys64\\mingw64\\bin;C:\\Tools\\msys2\\mingw64\\bin")
    }
}
