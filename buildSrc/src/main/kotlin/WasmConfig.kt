/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.io.*

fun Project.configureWasm() {
    configureWasmTasks()

    kotlin {
        sourceSets {
            val wasmJsTest by getting {
                dependencies {
                    implementation(npm("puppeteer", Versions.puppeteer))
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
                testTask(
                    Action {
                        useMocha {
                            timeout = "10000"
                        }
                    }
                )
            }

            browser {
                testTask(
                    Action {
                        useKarma {
                            useChromeHeadless()
                            useConfigDirectory(File(project.rootProject.projectDir, "karma"))
                        }
                    }
                )
            }
        }
    }
}

private fun Project.configureWasmTestTasks() {
    val shouldRunWasmBrowserTest = !hasProperty("teamcity") || hasProperty("enable-js-tests")
    if (shouldRunWasmBrowserTest) return

    val cleanWasmJsBrowserTest by tasks.getting
    val wasmJsBrowserTest by tasks.getting
    cleanWasmJsBrowserTest.onlyIf { false }
    wasmJsBrowserTest.onlyIf { false }
}
