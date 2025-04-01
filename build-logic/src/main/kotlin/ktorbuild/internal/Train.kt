/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.kotlin.dsl.withType

fun Project.setupTrain() {
    if (!buildSnapshotTrain) return

    if (name == "ktor-client") {
        println("Manifest of kotlin-compiler-embeddable.jar")
        printManifest()
    }

    configureVersion()
    filterSnapshotTests()
}

// Hacking test tasks, removing stress and flaky tests"
private fun Project.filterSnapshotTests() {
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

private fun Project.configureVersion() {
    version = providers.gradleProperty("DeployVersion").orNull ?: return
    val skipSnapshotChecks = providers.gradleProperty("skip_snapshot_checks").orNull.toBoolean()

    if (buildSnapshotTrain && !skipSnapshotChecks) {
        check(version, libs.versions.atomicfu, "atomicfu")
        check(version, libs.versions.coroutines, "coroutines")
        check(version, libs.versions.serialization, "serialization")
    }
}

private val Project.buildSnapshotTrain: Boolean
    get() = providers.gradleProperty("build_snapshot_train").orNull.toBoolean()

private fun check(version: Any, libVersionProvider: Provider<String>, libName: String) {
    val libVersion = libVersionProvider.get()
    check(version == libVersion) {
        "Current deploy version is $version, but $libName version is not overridden ($libVersion)"
    }
}
