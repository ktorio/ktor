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
    val hasNix = hasPosix || files.any { it.name == "nix" }
    val hasDarwin = hasNix || files.any { it.name == "darwin" }
    val hasJs = hasCommon || files.any { it.name == "js" }
    val hasJvm = hasCommon || files.any { it.name == "jvm" }

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

        if (IDEA_ACTIVE) {
            if (hasPosix) createIdeaTarget("posix")
            if (hasNix) createIdeaTarget("nix")
            if (hasDarwin) createIdeaTarget("darwin")
            if (hasDesktop) createIdeaTarget("desktop")
        } else {
            sourceSets {
                if (hasPosix) {
                    val posixMain by creating
                    val posixTest by creating
                }
                if (hasNix) {
                    val nixMain by creating
                    val nixTest by creating
                }
                if (hasDarwin) {
                    val darwinMain by creating
                    val darwinTest by creating
                }
                if (hasDesktop) {
                    val desktopMain by creating
                    val desktopTest by creating
                }
            }
        }

        sourceSets {
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

                extra.set("commonStructure", true)
            }
            if (hasJvmAndNix) {
                val jvmAndNixMain by creating {
                    findByName("commonMain")?.let { dependsOn(it) }
                }

                val jvmAndNixTest by creating {
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

                nixTargets().forEach {
                    getByName("${it.name}Main").dependsOn(nixMain)
                    getByName("${it.name}Test").dependsOn(nixTest)
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

                    if (!it.name.startsWith(HOST_NAME)) {
                        disableCompilation(it)
                    }
                }
            }
        }
    }

    tasks.findByName("mingwX64Test")?.apply {
        if (this !is KotlinNativeTest) return@apply
        environment("PATH", "C:\\msys64\\mingw64\\bin;C:\\Tools\\msys64\\mingw64\\bin;C:\\Tools\\msys2\\mingw64\\bin")
    }
}
