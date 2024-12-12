/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import internal.capitalized
import internal.libs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsSubTargetDsl

fun Project.configureJs() {
    kotlin {
        js {
            if (project.targetIsEnabled("js.nodeJs")) nodejs { useMochaForTests() }
            if (project.targetIsEnabled("js.browser")) browser { useKarmaForTests() }

            binaries.library()
        }

        sourceSets {
            jsTest {
                dependencies {
                    implementation(npm("puppeteer", libs.versions.puppeteer.get()))
                }
            }
        }
    }

    configureJsTestTasks(target = "js")
}

internal fun KotlinJsSubTargetDsl.useMochaForTests() {
    testTask {
        useMocha {
            // Disable timeout as we use individual timeouts for tests
            timeout = "0"
        }
    }
}

internal fun KotlinJsSubTargetDsl.useKarmaForTests() {
    testTask {
        useKarma {
            useChromeHeadless()
            useConfigDirectory(project.rootProject.file("karma"))
        }
    }
}

internal fun Project.configureJsTestTasks(target: String) {
    val shouldRunJsBrowserTest = !hasProperty("teamcity") || hasProperty("enable-js-tests")
    if (shouldRunJsBrowserTest) return

    tasks.maybeNamed("clean${target.capitalized()}BrowserTest") { onlyIf { false } }
    tasks.maybeNamed("${target}BrowserTest") { onlyIf { false } }
}
