/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

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

    val kotlinVersion = project.findProperty("kotlin_version") as? String
    val slf4jVersion = rootProject.versionCatalog.findVersion("slf4j-version").get().requiredVersion
    val junitVersion = rootProject.versionCatalog.findVersion("junit-version").get().requiredVersion
    val coroutinesVersion = project.findProperty("coroutines_version") as? String

    val configuredVersion: String by rootProject.extra

    kotlin {
        jvm()

        sourceSets.apply {
            val jvmMain by getting {
                dependencies {
                    if (jdk > 6) {
                        api("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")
                        println("JvmConfig starting with kotlin version $kotlinVersion")
                    }
                    if (jdk > 7) {
                        api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
                        api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion") {
                            exclude(module = "kotlin-stdlib")
                            exclude(module = "kotlin-stdlib-jvm")
                            exclude(module = "kotlin-stdlib-jdk8")
                            exclude(module = "kotlin-stdlib-jdk7")
                            println("JvmConfig starting with coroutines version $coroutinesVersion")
                        }
                    }

                    api("org.slf4j:slf4j-api:$slf4jVersion")
                }
            }

            val jvmTest by getting {
                dependencies {
                    implementation(kotlin("test-junit5"))
                    implementation("org.junit.jupiter:junit-jupiter:$junitVersion")
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutinesVersion")
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
        jvmArgs("-XX:+HeapDumpOnOutOfMemoryError")
        exclude("**/*StressTest*")
        useJUnitPlatform()
        configureJavaLauncher(jdk)
    }

    tasks.create<Test>("stressTest") {
        classpath = files(jvmTest.classpath)
        testClassesDirs = files(jvmTest.testClassesDirs)

        ignoreFailures = true
        maxHeapSize = "2g"
        jvmArgs("""-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./oom_dump.hprof""")
        setForkEvery(1)
        systemProperty("enable.stress.tests", "true")
        include("**/*StressTest*")
        useJUnitPlatform()
        configureJavaLauncher(jdk)
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
