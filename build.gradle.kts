/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.dokka.gradle.*
import org.jetbrains.kotlin.konan.target.*

buildscript {
    /*
     * These property group is used to build ktor against Kotlin compiler snapshot.
     * How does it work:
     * When build_snapshot_train is set to true, kotlin_version property is overridden with kotlin_snapshot_version,
     * atomicfu_version, coroutines_version, serialization_version and kotlinx_io_version are overwritten by TeamCity environment.
     * Additionally, mavenLocal and Sonatype snapshots are added to repository list and stress tests are disabled.
     * DO NOT change the name of these properties without adapting kotlinx.train build chain.
     */
    extra["build_snapshot_train"] = rootProject.properties["build_snapshot_train"].let { it != null && it != "" }
    val build_snapshot_train: Boolean by extra

    if (build_snapshot_train) {
        extra["kotlin_version"] = rootProject.properties["kotlin_snapshot_version"]
        val kotlin_version: String? by extra
        if (kotlin_version == null) {
            throw IllegalArgumentException(
                "'kotlin_snapshot_version' should be defined when building with snapshot compiler"
            )
        }
        repositories {
            mavenLocal()
            maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
        }

        configurations.classpath {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jetbrains.kotlin") {
                    useVersion(kotlin_version!!)
                }
            }
        }
    }

    // This flag is also used in settings.gradle to exclude native-only projects
    extra["native_targets_enabled"] = rootProject.properties["disable_native_targets"] == null

    repositories {
        mavenLocal()
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    }
}

val releaseVersion: String? by extra
val eapVersion: String? by extra
val native_targets_enabled: Boolean by extra
val version = (project.version as String).dropLast("-SNAPSHOT".length)

extra["configuredVersion"] = when {
    releaseVersion != null -> releaseVersion
    eapVersion != null -> "$version-eap-$eapVersion"
    else -> project.version
}

println("The build version is ${extra["configuredVersion"]}")

extra["globalM2"] = "$buildDir/m2"
extra["publishLocal"] = project.hasProperty("publishLocal")

val configuredVersion: String by extra

apply(from = "gradle/verifier.gradle")

extra["skipPublish"] = mutableListOf<String>()
extra["nonDefaultProjectStructure"] = mutableListOf(
    "ktor-bom",
    "ktor-java-modules-test"
)

val disabledExplicitApiModeProjects = listOf(
    "ktor-client-tests",
    "ktor-client-json-tests",
    "ktor-server-test-host",
    "ktor-server-test-suites",
    "ktor-server-tests",
    "ktor-client-content-negotiation-tests",
)

apply(from = "gradle/compatibility.gradle")

plugins {
    id("org.jetbrains.dokka") version "1.6.10" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.8.0"
    id("kotlinx-atomicfu") version "0.17.1" apply false
}

allprojects {
    group = "io.ktor"
    version = configuredVersion
    extra["hostManager"] = HostManager()

    setupTrainForSubproject()

    repositories {
        mavenLocal()
        mavenCentral()
        maven(url = "https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    }

    val nonDefaultProjectStructure: List<String> by rootProject.extra
    if (nonDefaultProjectStructure.contains(project.name)) return@allprojects

    apply(plugin = "kotlin-multiplatform")
    apply(plugin = "kotlinx-atomicfu")

    configureTargets()

    configurations {
        maybeCreate("testOutput")
    }

    kotlin {
        targets.all {

            if (this is org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget) {
                irTarget?.compilations?.all {
                    configureCompilation()
                }
            }
            compilations.all {
                configureCompilation()
            }
        }

        if (!disabledExplicitApiModeProjects.contains(project.name)) {
            explicitApi()
        }

        sourceSets
            .matching { it.name !in listOf("main", "test") }
            .all {
                val srcDir = if (name.endsWith("Main")) "src" else "test"
                val resourcesPrefix = if (name.endsWith("Test")) "test-" else ""
                val platform = name.dropLast(4)

                kotlin.srcDir("$platform/$srcDir")
                resources.srcDir("$platform/${resourcesPrefix}resources")

                languageSettings.apply {
                    progressiveMode = true
                }
            }

        val jdk = when (name) {
            in jdk11Modules -> 11
            else -> 8
        }

        jvmToolchain {
            check(this is JavaToolchainSpec)
            languageVersion.set(JavaLanguageVersion.of(jdk))
        }
    }

    val skipPublish: List<String> by rootProject.extra
    if (!skipPublish.contains(project.name)) {
        configurePublication()
    }
}

subprojects {
    configureCodestyle()
}

println("Using Kotlin compiler version: ${org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION}")
filterSnapshotTests()

allprojects {
    plugins.apply("org.jetbrains.dokka")

    val dokkaPlugin by configurations
    dependencies {
        dokkaPlugin("org.jetbrains.dokka:all-modules-page-plugin:1.6.10")
        dokkaPlugin("org.jetbrains.dokka:versioning-plugin:1.6.10")
    }
}

val dokkaOutputDir = "../versions"

tasks.withType<DokkaMultiModuleTask> {
    val mapOf = mapOf(
        "org.jetbrains.dokka.versioning.VersioningPlugin" to
            """{ "version": "$configuredVersion", "olderVersionsDir":"$dokkaOutputDir" }"""
    )

    outputDirectory.set(file(projectDir.toPath().resolve(dokkaOutputDir).resolve(configuredVersion)))
    pluginsMapConfiguration.set(mapOf)
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().ignoreScripts = false
}
