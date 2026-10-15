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

    tasks.withType<Test>().matching { it.name in ANNOTATION_DRIVEN_TEST_TASKS }.configureEach {
        maxHeapSize = "2g"
        systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
        systemProperty(FLAKY_MODE_PROPERTY, flakyTestsMode.propertyValue)
        useJUnitPlatform()
        configureJavaToolchain(java.toolchain.languageVersion, ktorBuild.jvmTestToolchain)
    }

    val jvmTest = tasks.named<KotlinJvmTest>("jvmTest") {
        exclude("**/*StressTest*")
    }

    tasks.register<Test>("stressTest") {
        inheritTestFilesFrom(jvmTest)
        jvmArgs("-XX:+HeapDumpOnOutOfMemoryError")
        setForkEvery(1)
        systemProperty("enable.stress.tests", "true")
        include("**/*StressTest*")
    }

    tasks.register<Test>(FLAKY_TEST_TASK) {
        inheritTestFilesFrom(jvmTest)
        inheritExecutionSettingsFrom(jvmTest)

        val classpath = classpath

        onlyIf("FlakyTestCondition is on the test runtime classpath") {
            classpath.any { it.invariantSeparatorsPath.contains(TEST_BASE_MODULE) }
        }

        ignoreFailures = true
        outputs.upToDateWhen { false }
        systemProperty(FLAKY_MODE_PROPERTY, FlakyTestsMode.ONLY.propertyValue)
        exclude("**/*StressTest*")
    }
}

/** Copies the test classpath and classes directories of [source] onto this task. */
private fun Test.inheritTestFilesFrom(source: TaskProvider<KotlinJvmTest>) {
    val jvmTest = source.get()
    classpath = jvmTest.classpath
    testClassesDirs = jvmTest.testClassesDirs
}

/**
 * Copies the execution environment of [source] onto this task: the system properties and JVM
 * arguments a module set on its own `jvmTest`, which decide how that module's tests behave.
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
