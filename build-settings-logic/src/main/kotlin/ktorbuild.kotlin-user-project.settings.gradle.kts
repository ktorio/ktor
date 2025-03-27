/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.*

/*
 * These properties are used to build Ktor against Kotlin compiler snapshot in two different configurations:
 *
 * Ktor K2:
 *   - `kotlin_version` the Kotlin version to be used for project compilation
 *   - `kotlin_repo_url` defines additional repository to be added to the repository list
 *   - `kotlin_language_version` overrides Kotlin language versions
 *   - `kotlin_api_version` overrides Kotlin API version
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
                log("${project.path} : Set Kotlin LV $version")
                languageVersion = version
            }
            kotlinApiVersion?.let { version ->
                log("${project.path} : Set Kotlin APIV $version")
                apiVersion = version
            }
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
    tasks.withType<Test>().configureEach {
        exclude("**/*ServerSocketTest*")
        exclude("**/*NettyStressTest*")
        exclude("**/*CIOMultithreadedTest*")
        exclude("**/*testBlockingConcurrency*")
        exclude("**/*testBigFile*")
        exclude("**/*numberTest*")
        exclude("**/*testWithPause*")
        exclude("**/*WebSocketTest*")
        exclude("**/*PostTest*")
        exclude("**/*testCustomUrls*")
        exclude("**/*testStaticServeFromDir*")
        exclude("**/*testRedirect*")
        exclude("**/*CIOHttpsTest*")
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
