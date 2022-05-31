/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("UNUSED_VARIABLE")

import org.gradle.api.*
import org.gradle.api.tasks.testing.*
import org.gradle.jvm.tasks.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.*

fun Project.configureJvm() {
    val jdk = when (name) {
        in jdk11Modules -> 11
        else -> 8
    }

    val kotlin_version: String by extra
    val slf4j_version: String by extra
    val junit_version: String by extra
    val coroutines_version: String by extra

    val configuredVersion: String by rootProject.extra

    kotlin {
        jvm()

        sourceSets.apply {
            val jvmMain by getting {
                dependencies {
                    if (jdk > 6) {
                        api("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlin_version")
                    }
                    if (jdk > 7) {
                        api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version")
                        api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutines_version") {
                            exclude(module = "kotlin-stdlib")
                            exclude(module = "kotlin-stdlib-jvm")
                            exclude(module = "kotlin-stdlib-jdk8")
                            exclude(module = "kotlin-stdlib-jdk7")
                        }
                    }

                    api("org.slf4j:slf4j-api:$slf4j_version")
                }
            }

            val jvmTest by getting {
                dependencies {
                    implementation("junit:junit:$junit_version")
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutines_version")
                }
            }
        }
    }

    tasks.register<Jar>("jarTest") {
        dependsOn(tasks.getByName("jvmTestClasses"))
        archiveClassifier.set("test")
        from(kotlin.targets.getByName("jvm").compilations.getByName("test").output)
    }

    configurations.apply {
        val testCompile = findByName("testCompile") ?: return@apply

        val testOutput by creating {
            extendsFrom(testCompile)
        }
        val boot by creating {
        }
    }

    val jvmTest: KotlinJvmTest = tasks.getByName<KotlinJvmTest>("jvmTest") {
        ignoreFailures = true
        maxHeapSize = "2g"
        exclude("**/*StressTest *")
    }

    tasks.create<Test>("stressTest") {
        classpath = files(jvmTest.classpath)
        testClassesDirs = files(jvmTest.testClassesDirs)

        ignoreFailures = true
        maxHeapSize = "2g"
        jvmArgs("-XX:+HeapDumpOnOutOfMemoryError")
        setForkEvery(1)
        systemProperty("enable.stress.tests", "true")
        include("**/*StressTest*")
    }

    tasks.getByName<Jar>("jvmJar").apply {
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

fun Project.javaModuleName(): String {
    return (if (this.name.startsWith("ktor-")) "io.${project.name}" else "io.ktor.${project.name}")
        .replace('-', '.')
        .replace("default.headers", "defaultheaders")
        .replace("double.receive", "doublereceive")
}
