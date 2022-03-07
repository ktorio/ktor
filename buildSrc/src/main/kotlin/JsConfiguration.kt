/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import org.gradle.api.*
import org.gradle.kotlin.dsl.*
import java.io.*

val skipModules = listOf(
    "ktor-client-cio"
)

fun Project.configureJsModules() {
    if (skipModules.contains(project.name)) return

    configureJsTasks()

    kotlin {
        sourceSets {
            val jsMain by getting {
            }

            val jsTest by getting {
                dependencies {
                    api(npm("puppeteer", "*"))
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

    val jsLegacyBrowserTest by tasks.getting
    jsLegacyBrowserTest.onlyIf { shouldRunJsBrowserTest }

    val jsIrBrowserTest by tasks.getting
    jsIrBrowserTest.onlyIf { shouldRunJsBrowserTest }
}
