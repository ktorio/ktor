/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("UNUSED_VARIABLE")

import org.gradle.api.*
import org.gradle.kotlin.dsl.*
import java.io.*

fun Project.configureJs() {
    configureJsTasks()

    kotlin {
        sourceSets {
            val jsTest by getting {
                dependencies {
                    implementation(npm("puppeteer", "*"))
                }
            }
        }
    }

    configureJsTestTasks()
}

private fun Project.configureJsTasks() {
    kotlin {
        js(IR) {
            nodejs {
                testTask {
                    useMocha {
                        timeout = "10000"
                    }
                }
            }

            browser {
                testTask {
                    useKarma {
                        useChromeHeadless()
                        useConfigDirectory(File(project.rootProject.projectDir, "karma"))
                    }
                }
            }

            val main by compilations.getting
            main.kotlinOptions.apply {
                metaInfo = true
                sourceMap = true
                moduleKind = "umd"
                this.main = "noCall"
                sourceMapEmbedSources = "always"
            }

            val test by compilations.getting
            test.kotlinOptions.apply {
                metaInfo = true
                sourceMap = true
                moduleKind = "umd"
                this.main = "call"
                sourceMapEmbedSources = "always"
            }
        }
    }
}

private fun Project.configureJsTestTasks() {
    val shouldRunJsBrowserTest = !hasProperty("teamcity") || hasProperty("enable-js-tests")
    if (shouldRunJsBrowserTest) return

    val cleanJsBrowserTest by tasks.getting
    val jsBrowserTest by tasks.getting
    cleanJsBrowserTest.onlyIf { false }
    jsBrowserTest.onlyIf { false }
}
