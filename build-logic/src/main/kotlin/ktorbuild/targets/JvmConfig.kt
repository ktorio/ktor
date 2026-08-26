/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.targets

import io.ktor.test.constants.FLAKY_MODE_PROPERTY
import io.ktor.test.constants.FlakyTestsMode
import ktorbuild.ProjectTag
import ktorbuild.addProjectTag
import ktorbuild.internal.java
import ktorbuild.internal.kotlin
import ktorbuild.internal.ktorBuild
import ktorbuild.internal.libs
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

/**
 * Identifies `ktor-test-base` on a test runtime classpath, where it appears either as this build's
 * output directories or as a resolved jar. It is the module shipping `FlakyTestCondition` — and the
 * `@Flaky` annotation the condition looks for.
 */
private const val TEST_BASE_MODULE = "ktor-test-base"

internal fun Project.configureJvm() {
    addProjectTag(ProjectTag.Jvm)

    kotlin {
        sourceSets {
            jvmMain.dependencies {
                api(libs.slf4j.api)
            }

            jvmTest.dependencies {
                implementation(libs.kotlin.test.junit5)
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutines.debug)
            }
        }
    }

    configureTests()
    configureJarManifest()
}

private fun Project.configureTests() {
    val flakyTestsMode = flakyTestsMode()

    // Take the test classpath from the JVM test *compilation*, not from the `jvmTest` task. A
    // provider derived from `tasks.named("jvmTest")` carries that task as its producer, so depending
    // on it makes Gradle run the whole default suite before `stressTest` or `flakyTest`. The
    // compilation's own file collections are built by the compile tasks, which is all these need.
    val jvmTestCompilation = kotlin.jvm().compilations.named("test")
    val jvmTestRuntimeClasspath = files(
        jvmTestCompilation.map { it.output.allOutputs },
        jvmTestCompilation.map { it.runtimeDependencyFiles },
    )
    val jvmTestClassesDirs = files(jvmTestCompilation.map { it.output.classesDirs })

    val jvmTest = tasks.named<KotlinJvmTest>("jvmTest") {
        maxHeapSize = "2g"
        exclude("**/*StressTest*")
        // Auto-register FlakyTestCondition so @Flaky tests are excluded from the default run (they
        // run only in the `flakyTest` task below, or with -Pktor.tests.flaky=only|all).
        systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
        systemProperty(FLAKY_MODE_PROPERTY, flakyTestsMode.propertyValue)
        useJUnitPlatform()
        configureJavaToolchain(java.toolchain.languageVersion, ktorBuild.jvmTestToolchain)
    }

    tasks.register<Test>("stressTest") {
        classpath = jvmTestRuntimeClasspath
        testClassesDirs = jvmTestClassesDirs

        maxHeapSize = "2g"
        jvmArgs("-XX:+HeapDumpOnOutOfMemoryError")
        setForkEvery(1)
        systemProperty("enable.stress.tests", "true")
        // JVM test tasks are exempt from the `_flaky` name filter (see `configureFlakyTests`), so
        // the condition is what keeps @Flaky tests out of this task too.
        systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
        systemProperty(FLAKY_MODE_PROPERTY, flakyTestsMode.propertyValue)
        include("**/*StressTest*")
        useJUnitPlatform()
        configureJavaToolchain(java.toolchain.languageVersion, ktorBuild.jvmTestToolchain)
    }

    // Runs ONLY @Flaky-annotated tests (excluded from the default `jvmTest`). Intended for a
    // nightly/dedicated job that publishes to Develocity so quarantined tests stay tracked.
    // (Selection is by annotation, resolved at execution time; the `_flaky` name token — same
    // property, applied by `configureFlakyTests` — is the equivalent for Native/JS/Wasm, which have
    // no JUnit Platform.)
    tasks.register<Test>(FLAKY_TEST_TASK) {
        classpath = jvmTestRuntimeClasspath
        testClassesDirs = jvmTestClassesDirs
        // Per-module `jvmTest` tweaks decide how a module's tests behave — Netty and the Jetty HTTP/2
        // modules set `enable.http2`, the Android client sets `http.maxConnections`. Without them a
        // quarantined test would run under different conditions here than in the suite it was
        // quarantined from, corrupting the very flip rate this task exists to measure.
        inheritExecutionSettingsFrom(jvmTest)

        // Selection here is by annotation, and the condition that applies it ships in
        // `ktor-test-base`. A module that doesn't have it on the test runtime classpath has nothing
        // deselecting its ordinary tests, so this task would run the module's whole suite — with
        // `ignoreFailures` below hiding every failure it produced. Such a module can't have @Flaky
        // tests in the first place, since the annotation ships in the same module, so skipping is
        // the correct outcome rather than a missed sample.
        onlyIf("FlakyTestCondition is on the test runtime classpath") {
            jvmTestRuntimeClasspath.any { it.invariantSeparatorsPath.contains(TEST_BASE_MODULE) }
        }

        maxHeapSize = "2g"
        // A quarantined test flipping is the expected outcome here, not a regression to block on.
        // Failures still land in the test reports and in Develocity, which is where the flip rate is tracked.
        ignoreFailures = true
        // Each run is a fresh sample of whether the test still flips, so an unchanged input tree is
        // no reason to skip it — up-to-date checking would report the previous run's verdict forever.
        outputs.upToDateWhen { false }
        systemProperty(FLAKY_MODE_PROPERTY, FlakyTestsMode.ONLY.propertyValue)
        systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
        exclude("**/*StressTest*")
        useJUnitPlatform()
        configureJavaToolchain(java.toolchain.languageVersion, ktorBuild.jvmTestToolchain)
    }
}

/**
 * Copies the execution environment of [source] onto this task: the system properties and JVM
 * arguments a module set on its own `jvmTest`, which decide how that module's tests behave.
 *
 * `get()` realizes the task to read its configuration, which does *not* make this task depend on
 * running it — unlike deriving a `FileCollection` from the provider, which does. Anything this task
 * sets afterwards wins, so the flaky mode and the toolchain settings still override what is copied.
 */
private fun Test.inheritExecutionSettingsFrom(source: TaskProvider<KotlinJvmTest>) {
    val jvmTest = source.get()
    systemProperties(jvmTest.systemProperties)
    jvmArgs(jvmTest.jvmArgs.orEmpty())
}

private fun Project.configureJarManifest() {
    tasks.named<Jar>("jvmJar") {
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Automatic-Module-Name" to project.javaModuleName(),
            )
        }
    }
}

/** Configure tests against different JDK versions. */
private fun Test.configureJavaToolchain(
    compileJdk: Provider<JavaLanguageVersion>,
    testJdk: Provider<JavaLanguageVersion>,
) {
    val testJdkVersion = testJdk.get().asInt()
    onlyIf("only if testJdk is not lower than compileJdk") { testJdkVersion >= compileJdk.get().asInt() }

    val javaToolchains = project.the<JavaToolchainService>()
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = testJdk
    }

    if (testJdkVersion >= 16) {
        // Allow reflective access from tests
        jvmArgs(
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.time=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
        )
    }

    if (testJdkVersion >= 21) {
        // coroutines-debug use dynamic agent loading under the hood.
        // Remove as soon as the issue is fixed: https://youtrack.jetbrains.com/issue/KT-62096/
        jvmArgs("-XX:+EnableDynamicAgentLoading")
    }
}

fun Project.javaModuleName(): String {
    check(name.startsWith("ktor-")) { "Project name should start with prefix 'ktor-'." }

    return "io.$name"
        .replace('-', '.')
        .replace("default.headers", "defaultheaders")
        .replace("double.receive", "doublereceive")
}
