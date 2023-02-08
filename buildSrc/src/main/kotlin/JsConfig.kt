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

    configureTestTask()
}

private fun Project.configureJsTasks() {
    kotlin {
        js {
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

private fun Project.configureTestTask() {
    val shouldRunJsBrowserTest = !hasProperty("teamcity") || hasProperty("enable-js-tests")

    val jsBrowserTest by tasks.getting
    val jsNodeTest by tasks.getting
    val cleanJsBrowserTest by tasks.getting
    val cleanJsNodeTest by tasks.getting
    jsBrowserTest.onlyIf { shouldRunJsBrowserTest }

    val jsIrBrowserTest by tasks.creating {
        dependsOn(jsBrowserTest)
    }

    val jsIrNodeTasks by tasks.creating {
        dependsOn(jsNodeTest)
    }

    val cleanJsIrBrowserTest by tasks.creating {
        dependsOn(cleanJsBrowserTest)
    }

    val cleanJsIrNodeTasks by tasks.creating {
        dependsOn(cleanJsNodeTest)
    }
}
