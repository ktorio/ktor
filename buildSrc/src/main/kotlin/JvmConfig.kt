/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import internal.*
import org.gradle.api.*
import org.gradle.api.tasks.testing.*
import org.gradle.jvm.tasks.*
import org.gradle.jvm.toolchain.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.*

fun Project.configureJvm() {
    val jdk = when (name) {
        in jdk11Modules -> 11
        else -> 8
    }

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

    val jvmTest = tasks.named<KotlinJvmTest>("jvmTest") {
        maxHeapSize = "2g"
        exclude("**/*StressTest*")
        useJUnitPlatform()
        configureJavaLauncher(jdk)
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
        configureJavaLauncher(jdk)
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

/**
 * JUnit 5 requires Java 11+
 */
fun Test.configureJavaLauncher(jdk: Int) {
    if (jdk < 11) {
        val javaToolchains = project.extensions.getByType<JavaToolchainService>()
        val customLauncher = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of("11")
        }
        javaLauncher = customLauncher
    }
}

fun Project.javaModuleName(): String {
    return (if (this.name.startsWith("ktor-")) "io.${project.name}" else "io.ktor.${project.name}")
        .replace('-', '.')
        .replace("default.headers", "defaultheaders")
        .replace("double.receive", "doublereceive")
}
