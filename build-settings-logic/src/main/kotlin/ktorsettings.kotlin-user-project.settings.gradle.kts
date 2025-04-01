/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UnstableApiUsage")

import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.*

/*
 * These properties are used to build Ktor against Kotlin compiler snapshot in two different configurations:
 *
 * Ktor K2:
 *   - `kotlin_version` the Kotlin version to be used for project compilation
 *   - `kotlin_repo_url` defines additional repository to be added to the repository list
 *   - `kotlin_language_version` overrides Kotlin language versions
 *   - `kotlin_api_version` overrides Kotlin API version
 *   - `kotlin_additional_cli_options` additional CLI options for the Kotlin compiler
 *
 * Ktor Train:
 *   All the above properties are applied, and:
 *   - `build_snapshot_train` is set to true
 *   - `atomicfu_version`, `coroutines_version` and `serialization_version` are defined in TeamCity environment
 *   - Additionally, some tests are disabled.
 *
 * DO NOT change the names of these properties without adapting kotlinx.train build chain.
 *
 * Docs:
 * - https://youtrack.jetbrains.com/articles/KT-A-523/K2-User-Projects-Infrastructure-Requirements
 * - https://youtrack.jetbrains.com/articles/KT-A-123/Kotlinx-Train
 */

val buildSnapshotTrain by lazy {
    providers.gradleProperty("build_snapshot_train").orNull
        ?.also { log("Build snapshot train: $it") }
        .toBoolean()
}
val kotlinRepoUrl by lazy {
    providers.gradleProperty("kotlin_repo_url").orNull
        ?.also { log("Kotlin Dev repository: $it") }
}
val kotlinVersion by lazy {
    providers.gradleProperty("kotlin_version").orNull
        ?.also { log("Kotlin version: $it") }
}
val kotlinLanguageVersion by lazy {
    providers.gradleProperty("kotlin_language_version")
        .map(KotlinVersion::fromVersion)
        .orNull
        ?.also { log("Kotlin Language version: ${it.version}") }
}
val kotlinApiVersion by lazy {
    providers.gradleProperty("kotlin_api_version")
        .map(KotlinVersion::fromVersion)
        .orNull
        ?.also { log("Kotlin API version: ${it.version}") }
}
val kotlinAdditionalCliOptions by lazy {
    val spacesRegex = "\\s+".toRegex()
    providers.gradleProperty("kotlin_additional_cli_options")
        .map { it.trim { it == '"' || it.isWhitespace() }.split(spacesRegex).filterNot(String::isEmpty) }
        .orNull
        ?.also { log("Kotlin additional CLI options: $it") }
        .orEmpty()
}

pluginManagement {
    repositories {
        kotlinDev()
    }
}

dependencyResolutionManagement {
    repositories {
        kotlinDev()
    }

    versionCatalogs {
        named("libs") {
            kotlinVersion?.let { version("kotlin", it) }

            if (buildSnapshotTrain) {
                checkNotNull(kotlinVersion) {
                    "kotlin_version should be specified when building with build_snapshot_train=true"
                }
                overrideKotlinxVersions()
            }
        }
    }
}

gradle.afterProject {
    if (extensions.findByName("kotlin") == null) return@afterProject

    extensions.configure<HasConfigurableKotlinCompilerOptions<*>>("kotlin") {
        compilerOptions {
            kotlinLanguageVersion?.let { version ->
                languageVersion = version
                log("$path : Set Kotlin LV $version")
            }

            kotlinApiVersion?.let { version ->
                apiVersion = version
                log("$path : Set Kotlin APIV $version")
            }

            // Unconditionally disable the -Werror option
            allWarningsAsErrors = false

            val argsToAdd = listOf(
                // Output reported warnings even in the presence of reported errors
                "-Xreport-all-warnings",
                // Output kotlin.git-searchable names of reported diagnostics
                "-Xrender-internal-diagnostic-names",
                // Opt into additional warning-reporting compilation checks
                "-Wextra",
                // Opt into additional compilation checks hidden from users
                "-Xuse-fir-experimental-checkers",
            ) + kotlinAdditionalCliOptions

            freeCompilerArgs.addAll(argsToAdd)
            log("$path : Added ${argsToAdd.joinToString(" ")}")
        }
    }

    if (buildSnapshotTrain) {
        if (name == "ktor-client") {
            println("Manifest of kotlin-compiler-embeddable.jar")
            printManifest()
        }
        filterTests()
    }
}

private fun VersionCatalogBuilder.overrideKotlinxVersions() {
    fun overrideVersion(name: String) {
        val version = providers.gradleProperty("${name}_version").get()
        log("Overriding $name version to $version")
        version(name, version)
    }

    overrideVersion("atomicfu")
    overrideVersion("coroutines")
    overrideVersion("serialization")
}

private fun RepositoryHandler.kotlinDev() {
    kotlinRepoUrl?.let { url ->
        maven(url) { name = "KotlinDev" }
    }
}

/** Hacking test tasks, removing stress and flaky tests */
private fun Project.filterTests() {
    tasks.withType<AbstractTestTask>().configureEach {
        with(filter) {
            // Groups
            excludeTestsMatching("*StressTest")

            // Classes
            excludeTestsMatching("*.CIOHttpsTest")
            excludeTestsMatching("*.CIOMultithreadedTest")
            excludeTestsMatching("*.HttpRedirectTest")
            excludeTestsMatching("*.PostTest")
            excludeTestsMatching("*.ServerSocketTest")
            excludeTestsMatching("*.WebSocketTest")

            // Particular tests
            excludeTestsMatching("*numberTest")
            excludeTestsMatching("*testBigFile*")
            excludeTestsMatching("*testBlockingConcurrency")
            excludeTestsMatching("*testCustomUrls")
            excludeTestsMatching("*testStaticServeFromDir")
        }
    }
}

private fun Project.printManifest() {
    configurations.matching { it.name == "kotlinCompilerClasspath" }.all {
        files.filter { it.name.contains("kotlin-compiler-embeddable") }.forEach { file ->
            val manifest = zipTree(file)
                .matching { include("META-INF/MANIFEST.MF") }
                .files.first()

            manifest.useLines { println(it) }
        }
    }
}

private fun log(message: String) {
    logger.info("<KUP> $message")
}
