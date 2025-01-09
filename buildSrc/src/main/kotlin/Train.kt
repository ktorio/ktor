/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import internal.libs
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

fun Project.filterSnapshotTests() {
    if (!buildSnapshotTrain) return

    println("Hacking test tasks, removing stress and flaky tests")
    subprojects {
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

    println("Manifest of kotlin-compiler-embeddable.jar")

    subprojects.filter { it.name == "ktor-client" }.forEach {
        configurations.matching { it.name == "kotlinCompilerClasspath" }.all {
            resolvedConfiguration.files.filter { it.name.contains("kotlin-compiler-embeddable") }.forEach {
                val manifest = zipTree(it).matching {
                    include("META-INF/MANIFEST.MF")
                }.files.first()

                manifest.readLines().forEach {
                    println(it)
                }
            }
        }
    }
}

fun Project.setupTrainForSubproject() {
    val deployVersion = properties["DeployVersion"]
    if (deployVersion != null) version = deployVersion

    if (buildSnapshotTrain && !rootProject.hasProperty("skip_snapshot_checks")) {
        check(version, rootProject.libs.versions.atomicfu, "atomicfu")
        check(version, rootProject.libs.versions.coroutines, "coroutines")
        check(version, rootProject.libs.versions.serialization, "serialization")
    }
}

private val Project.buildSnapshotTrain: Boolean
    get() = rootProject.findProperty("build_snapshot_train")?.toString().toBoolean()

private fun check(version: Any, libVersionProvider: Provider<String>, libName: String) {
    val libVersion = libVersionProvider.get()
    check(version == libVersion) {
        "Current deploy version is $version, but $libName version is not overridden ($libVersion)"
    }
}

private var resolvedKotlinApiVersion: KotlinVersion? = null

fun Project.getKotlinApiVersion(): KotlinVersion =
    resolvedKotlinApiVersion ?: resolveKotlinApiVersion().also { resolvedKotlinApiVersion = it }

private fun Project.resolveKotlinApiVersion(): KotlinVersion {
    val apiVersion = rootProject.findProperty("kotlin_api_version")
        ?.let { KotlinVersion.fromVersion(it.toString()) }
        ?: KotlinVersion.KOTLIN_2_0
    logger.info("Kotlin API version: $apiVersion")

    return apiVersion
}

private var resolvedKotlinLanguageVersion: KotlinVersion? = null

fun Project.getKotlinLanguageVersion(): KotlinVersion =
    resolvedKotlinLanguageVersion ?: resolveKotlinLanguageVersion().also { resolvedKotlinLanguageVersion = it }

private fun Project.resolveKotlinLanguageVersion(): KotlinVersion {
    val languageVersion = rootProject.findProperty("kotlin_language_version")
        ?.let { KotlinVersion.fromVersion(it.toString()) }
        ?: KotlinVersion.KOTLIN_2_1
    logger.info("Kotlin language version: $languageVersion")

    return languageVersion
}
