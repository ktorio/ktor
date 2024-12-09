/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import internal.libs
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

fun Project.configureJvm() {
    val compileJdk = project.requiredJdkVersion

    kotlin {
        jvm()

        sourceSets {
            jvmMain {
                dependencies {
                    api(libs.slf4j.api)
                }
            }

            jvmTest {
                dependencies {
                    implementation(libs.kotlin.test.junit5)
                    implementation(libs.junit)
                    implementation(libs.kotlinx.coroutines.debug)
                }
            }
        }
    }

    tasks.register<Jar>("jarTest") {
        dependsOn(tasks.named("jvmTestClasses"))
        archiveClassifier = "test"
        from(kotlin.jvm().compilations["test"].output)
    }

    configurations {
        val testCompile = findByName("testCompile") ?: return@configurations

        val testOutput by creating {
            extendsFrom(testCompile)
        }
        val boot by creating {
        }
    }

    val testJdk = project.testJdk
    val jvmTest = tasks.named<KotlinJvmTest>("jvmTest") {
        maxHeapSize = "2g"
        exclude("**/*StressTest*")
        useJUnitPlatform()
        configureJavaToolchain(compileJdk, testJdk)
    }

    tasks.register<Test>("stressTest") {
        classpath = files(jvmTest.get().classpath)
        testClassesDirs = files(jvmTest.get().testClassesDirs)

        maxHeapSize = "2g"
        jvmArgs("-XX:+HeapDumpOnOutOfMemoryError")
        setForkEvery(1)
        systemProperty("enable.stress.tests", "true")
        include("**/*StressTest*")
        useJUnitPlatform()
        configureJavaToolchain(compileJdk, testJdk)
    }

    val configuredVersion: String by rootProject.extra
    tasks.named<Jar>("jvmJar") {
        manifest {
            attributes(
                "Implementation-Title" to name,
                "Implementation-Version" to configuredVersion
            )
            val name = project.javaModuleName()
            attributes("Automatic-Module-Name" to name)
        }
    }
}

private var _testJdk = 0

/**
 * Retrieves the JDK version for running tests.
 *
 * Takes the version from property "test.jdk" or uses Gradle JDK by default.
 * For example, to run tests against JDK 8, run test task with flag "-Ptest.jdk=8"
 * or put this property to `gradle.properties`.
 */
private val Project.testJdk: Int
    get() {
        if (_testJdk == 0) {
            _testJdk = rootProject.properties["test.jdk"]?.toString()?.toInt() ?: currentJdk
            logger.info("Running tests against JDK $_testJdk")
        }
        return _testJdk
    }

/** Configure tests against different JDK versions. */
private fun Test.configureJavaToolchain(compileJdk: Int, testJdk: Int) {
    if (testJdk < compileJdk) {
        enabled = false
        return
    }

    val javaToolchains = project.the<JavaToolchainService>()
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(testJdk)
    }

    if (testJdk >= 16) {
        // Allow reflective access from tests
        jvmArgs(
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.time=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
        )
    }

    if (testJdk >= 21) {
        // coroutines-debug use dynamic agent loading under the hood.
        // Remove as soon as the issue is fixed: https://youtrack.jetbrains.com/issue/KT-62096/
        jvmArgs("-XX:+EnableDynamicAgentLoading")
    }
}

fun Project.javaModuleName(): String {
    return (if (this.name.startsWith("ktor-")) "io.${project.name}" else "io.ktor.${project.name}")
        .replace('-', '.')
        .replace("default.headers", "defaultheaders")
        .replace("double.receive", "doublereceive")
}
