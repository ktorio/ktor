/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import internal.*
import org.gradle.api.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.targets.js.ir.*
import java.io.*

fun Project.configureWasm() {
    configureWasmTasks()

    kotlin {
        sourceSets {
            wasmJsMain {
                dependencies {
                    implementation(libs.kotlinx.browser)
                }
            }
            wasmJsTest {
                dependencies {
                    implementation(npm("puppeteer", libs.versions.puppeteer.get()))
                }
            }
        }
    }

    configureWasmTestTasks()
}

private fun Project.configureWasmTasks() {
    kotlin {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            nodejs {
                testTask {
                    useMocha {
                        timeout = "10000"
                    }
                }
            }

            (this as KotlinJsIrTarget).whenBrowserConfigured {
                testTask {
                    useKarma {
                        useChromeHeadless()
                        useConfigDirectory(File(project.rootProject.projectDir, "karma"))
                    }
                }
            }
        }
    }
}

private fun Project.configureWasmTestTasks() {
    val shouldRunWasmBrowserTest = !hasProperty("teamcity") || hasProperty("enable-js-tests")
    if (shouldRunWasmBrowserTest) return

    tasks.maybeNamed("cleanWasmJsBrowserTest") { onlyIf { false } }
    tasks.maybeNamed("wasmJsBrowserTest") { onlyIf { false } }
}
