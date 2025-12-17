/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.targets

import ktorbuild.internal.capitalized
import ktorbuild.internal.gradle.maybeNamed
import ktorbuild.internal.kotlin
import ktorbuild.internal.libs
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsSubTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.web.nodejs.BaseNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.web.yarn.BaseYarnRootEnvSpec
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon

internal fun KotlinJsTargetDsl.addSubTargets(targets: KtorTargets) {
    if (targets.isEnabled("${targetName}.nodeJs")) nodejs {
        // Wasm uses a separate test framework. See KotlinWasmNode
        if (platformType != KotlinPlatformType.wasm) useMochaForTests()
    }
    if (targets.isEnabled("${targetName}.browser")) browser { useKarmaForTests() }
}

private fun KotlinJsSubTargetDsl.useMochaForTests() {
    testTask {
        useMocha {
            // Disable timeout as we use individual timeouts for tests
            timeout = "0"
        }
    }
}

private fun KotlinJsSubTargetDsl.useKarmaForTests() {
    testTask {
        useKarma {
            useChromeHeadless()
            useConfigDirectory(project.rootProject.file("karma"))
        }
    }
}

internal fun Project.configureJs() {
    kotlin.js {
        compilerOptions.configure()
        binaries.library()
    }

    configureJsTestTasks(target = "js")
}

@OptIn(ExperimentalWasmDsl::class)
internal fun Project.configureWasmJs() {
    kotlin.wasmJs { compilerOptions.configure() }

    configureJsTestTasks(target = "wasmJs")
}

internal fun Project.configureWeb() {
    kotlin {
        sourceSets {
            webMain.dependencies {
                implementation(libs.kotlinx.browser)
            }
            webTest.dependencies {
                // Puppeteer is used to install Chrome for tests
                implementation(npm("puppeteer", libs.versions.puppeteer.get()))
            }
        }
    }

    tasks.named { it == "compileWebMainKotlinMetadata" }.configureEach {
        (this as KotlinCompileCommon).compilerOptions.configure()
    }

    configureNodeJs()
}

private fun KotlinCommonCompilerOptions.configure() {
    optIn.add("kotlin.js.ExperimentalWasmJsInterop")
}

internal fun Project.configureJsTestTasks(target: String) {
    val shouldRunJsBrowserTest = !hasProperty("teamcity") || hasProperty("enable-js-tests")
    if (shouldRunJsBrowserTest) return

    tasks.maybeNamed("clean${target.capitalized()}BrowserTest") { onlyIf { false } }
    tasks.maybeNamed("${target}BrowserTest") { onlyIf { false } }
}

fun Project.configureNodeJs() {
    @Suppress("UnstableApiUsage")
    val nodeVersion = providers.fileContents(layout.settingsDirectory.file(".nvmrc"))
        .asText
        .map { it.trim() }

    plugins.withType<NodeJsPlugin> { the<NodeJsEnvSpec>().configure(nodeVersion) }
    plugins.withType<WasmNodeJsPlugin> { the<WasmNodeJsEnvSpec>().configure(nodeVersion) }
}

private fun BaseNodeJsEnvSpec.configure(nodeVersion: Provider<String>) {
    version = nodeVersion
    if (isKtorDevEnvironment) download = false
}

fun Project.configureYarn() {
    plugins.withType<YarnPlugin> { the<YarnRootEnvSpec>().configure() }
    plugins.withType<WasmYarnPlugin> { the<WasmYarnRootEnvSpec>().configure() }
}

private fun BaseYarnRootEnvSpec.configure() {
    if (isKtorDevEnvironment) download = false
    // Don't ignore scripts if we want Chrome to be installed automatically with puppeteer.
    if (shouldDownloadBrowser) ignoreScripts = false
}

// KTOR_DEV is set to `true` in the docker image used to build Ktor.
// This image has Node.js and Yarn bundled so this flag disables downloading of them.
private val isKtorDevEnvironment: Boolean
    get() = System.getenv("KTOR_DEV") == "true"

// If CHROME_BIN is undefined, puppeteer will install Chrome automatically
private val shouldDownloadBrowser: Boolean
    get() = System.getenv("CHROME_BIN") == null
