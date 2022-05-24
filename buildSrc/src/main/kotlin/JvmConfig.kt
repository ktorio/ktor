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

    val kotlinVersion = rootProject.versionCatalog.findVersion("kotlin-version").get().requiredVersion
    val slf4jVersion = rootProject.versionCatalog.findVersion("slf4j-version").get().requiredVersion
    val junitVersion = rootProject.versionCatalog.findVersion("junit-version").get().requiredVersion
    val coroutinesVersion = rootProject.versionCatalog.findVersion("coroutines-version").get().requiredVersion

    val configuredVersion: String by rootProject.extra

    kotlin {
        jvm()

        sourceSets {
            jvmMain {
                dependencies {
                    if (jdk > 6) {
                        api("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")
                    }
                    if (jdk > 7) {
                        api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
                        api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion") {
                            exclude(module = "kotlin-stdlib")
                            exclude(module = "kotlin-stdlib-jvm")
                            exclude(module = "kotlin-stdlib-jdk8")
                            exclude(module = "kotlin-stdlib-jdk7")
                        }
                    }

                    api("org.slf4j:slf4j-api:$slf4jVersion")
                }
            }

            jvmTest {
                dependencies {
                    implementation(kotlin("test-junit5"))
                    implementation("org.junit.jupiter:junit-jupiter:$junitVersion")
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutinesVersion")
                }
            }
        }
    }

    tasks.register<Jar>("jarTest") {
        dependsOn(tasks.named("jvmTestClasses"))
        archiveClassifier = "test"
        from(kotlin.jvm().compilations.named("test").map { it.output })
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
        jvmArgs("-XX:+HeapDumpOnOutOfMemoryError")
        exclude("**/*StressTest*")
        useJUnitPlatform()
        configureJavaLauncher(jdk)
    }

    tasks.register<Test>("stressTest") {
        classpath = files(jvmTest.get().classpath)
        testClassesDirs = files(jvmTest.get().testClassesDirs)

        maxHeapSize = "2g"
        jvmArgs("""-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./oom_dump.hprof""")
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
