/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.targets

import ktorbuild.ProjectTag
import ktorbuild.addProjectTag
import ktorbuild.internal.java
import ktorbuild.internal.kotlin
import ktorbuild.internal.ktorBuild
import ktorbuild.internal.libs
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

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
    val flakyTestsEnabled = flakyTestsMode() != FlakyTestsMode.EXCLUDE

    val jvmTest = tasks.named<KotlinJvmTest>("jvmTest") {
        maxHeapSize = "2g"
        exclude("**/*StressTest*")
        // Auto-register FlakyTestCondition so @Flaky tests are excluded from the default
        // run (they run only in the `flakyTest` task below, which sets enable.flaky.tests).
        systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
        systemProperty("enable.flaky.tests", flakyTestsEnabled)
        useJUnitPlatform()
        configureJavaToolchain(java.toolchain.languageVersion, ktorBuild.jvmTestToolchain)
    }

    tasks.register<Test>("stressTest") {
        classpath = files(jvmTest.map { it.classpath })
        testClassesDirs = files(jvmTest.map { it.testClassesDirs })

        maxHeapSize = "2g"
        jvmArgs("-XX:+HeapDumpOnOutOfMemoryError")
        setForkEvery(1)
        systemProperty("enable.stress.tests", "true")
        include("**/*StressTest*")
        useJUnitPlatform()
        configureJavaToolchain(java.toolchain.languageVersion, ktorBuild.jvmTestToolchain)
    }

    // Runs @Flaky-annotated tests (excluded from the default `jvmTest`). Intended for a
    // nightly/dedicated job that publishes to Develocity so quarantined tests stay tracked.
    // This runs the full suite with flaky tests enabled, because the annotation can only be
    // resolved at execution time. To run flaky tests and nothing else — on any target, not just
    // JVM — use the name-based selection instead: `-Pktor.tests.flaky=only`.
    tasks.register<Test>(FLAKY_TEST_TASK) {
        classpath = files(jvmTest.map { it.classpath })
        testClassesDirs = files(jvmTest.map { it.testClassesDirs })

        maxHeapSize = "2g"
        systemProperty("enable.flaky.tests", "true")
        systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
        exclude("**/*StressTest*")
        useJUnitPlatform()
        configureJavaToolchain(java.toolchain.languageVersion, ktorBuild.jvmTestToolchain)
    }
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
