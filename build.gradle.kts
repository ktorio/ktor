/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.dokka.gradle.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.targets.js.*
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.*
import org.jetbrains.kotlin.gradle.tasks.*
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
    extra["build_snapshot_train"] = rootProject.properties["build_snapshot_train"]
    val build_snapshot_train: String? by extra

    if (build_snapshot_train?.toBoolean() == true) {
        extra["kotlin_version"] = rootProject.properties["kotlin_snapshot_version"]
        val kotlin_version: String? by extra
        if (kotlin_version == null) {
            throw IllegalArgumentException(
                "'kotlin_snapshot_version' should be defined when building with snapshot compiler",
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

    extra["kotlin_repo_url"] = rootProject.properties["kotlin_repo_url"]
    val kotlin_repo_url: String? by extra

    repositories {
        mavenLocal()
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        if (kotlin_repo_url != null) {
            maven(kotlin_repo_url!!)
        }
    }

    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${rootProject.properties["atomicfu_version"]}")
    }
}

val releaseVersion: String? by extra
val eapVersion: String? by extra
val native_targets_enabled: Boolean by extra
val version = (project.version as String).let { if (it.endsWith("-SNAPSHOT")) it.dropLast("-SNAPSHOT".length) else it }

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

extra["skipPublish"] = mutableListOf(
    "ktor-server-test-base",
    "ktor-junit"
)
extra["nonDefaultProjectStructure"] = mutableListOf(
    "ktor-bom",
    "ktor-java-modules-test",
)

val disabledExplicitApiModeProjects = listOf(
    "ktor-client-tests",
    "ktor-server-test-base",
    "ktor-server-test-suites",
    "ktor-server-tests",
    "ktor-client-content-negotiation-tests",
    "ktor-junit"
)

apply(from = "gradle/compatibility.gradle")

plugins {
    id("org.jetbrains.dokka") version "1.9.20" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.14.0"
    id("kotlinx-atomicfu") version "0.23.1" apply false
    id("com.osacky.doctor") version "0.9.2"
}

doctor {
    enableTestCaching = false
}

val kotlinVersion = project.findProperty("kotlin_version") as? String
println("Starting build with  Kotlin version $kotlinVersion")

allprojects {
    group = "io.ktor"
    version = configuredVersion
    extra["hostManager"] = HostManager()

    setupTrainForSubproject()

    extra["kotlin_repo_url"] = rootProject.properties["kotlin_repo_url"]
    val kotlin_repo_url: String? by extra

    repositories {
        mavenLocal()
        mavenCentral()
        maven(url = "https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        if (kotlin_repo_url != null) {
            maven(kotlin_repo_url!!)
        }
    }

    val nonDefaultProjectStructure: List<String> by rootProject.extra
    if (nonDefaultProjectStructure.contains(project.name)) return@allprojects

    apply(plugin = "kotlin-multiplatform")
    apply(plugin = "kotlinx-atomicfu")

    configureTargets()

    configurations {
        maybeCreate("testOutput")
    }

    configurations.configureEach {
        if (isCanBeResolved) {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jetbrains.kotlin") {
                    useVersion(kotlinVersion)
                }
            }
        }
    }

    configurations.all {
        if (name == "metadataCompileClasspath") return@all
        resolutionStrategy.dependencySubstitution.all {
            if (name == "metadataCompileClasspath") return@all
            val requestedComponent = requested
            if (requestedComponent is ModuleComponentSelector &&
                requestedComponent.group == "org.jetbrains.kotlin" &&
                requestedComponent.module == "atomicfu"
            ) {
                if (name == "metadataCompileClasspath") return@all
                useTarget(
                    "${requestedComponent.group}:kotlinx-atomicfu-runtime:$kotlinVersion",
                    "Using proper kotlinx-atomicfu-runtime instead of Gradle plugin"
                )
            }
        }
    }

    kotlin {
        if (!disabledExplicitApiModeProjects.contains(project.name)) explicitApi()

        setCompilationOptions()
        configureSourceSets()
        setupJvmToolchain()
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

fun configureDokka() {
    allprojects {
        plugins.apply("org.jetbrains.dokka")

        val dokkaPlugin by configurations
        dependencies {
            dokkaPlugin("org.jetbrains.dokka:versioning-plugin:1.9.20")
        }
    }

    val dokkaOutputDir = "../versions"

    tasks.withType<DokkaMultiModuleTask> {
        val id = "org.jetbrains.dokka.versioning.VersioningPlugin"
        val config = """{ "version": "$configuredVersion", "olderVersionsDir":"$dokkaOutputDir" }"""
        val mapOf = mapOf(id to config)

        outputDirectory.set(file(projectDir.toPath().resolve(dokkaOutputDir).resolve(configuredVersion)))
        pluginsMapConfiguration.set(mapOf)
    }

    rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
        rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().ignoreScripts = false
    }
}

configureDokka()

fun Project.setupJvmToolchain() {
    val jdk = when (project.name) {
        in jdk11Modules -> 11
        else -> 8
    }

    kotlin {
        jvmToolchain {
            check(this is JavaToolchainSpec)
            languageVersion = JavaLanguageVersion.of(jdk)
        }
    }
}

fun KotlinMultiplatformExtension.setCompilationOptions() {
    targets.all {
        if (this is KotlinJsTarget) {
            irTarget?.compilations?.all {
                configureCompilation()
            }
        }
        compilations.all {
            configureCompilation()
        }
    }
}

fun KotlinMultiplatformExtension.configureSourceSets() {

    extra["kotlin_language_version"] = rootProject.properties["kotlin_language_version"]
    val kotlin_language_version: String? by extra

    extra["kotlin_api_version"] = rootProject.properties["kotlin_api_version"]
    val kotlin_api_version: String? by extra


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
                languageVersion = kotlin_language_version
                apiVersion = kotlin_api_version
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }
}

// Drop this configuration when the Node.JS version in KGP will support wasm gc milestone 4
// check it here:
// https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/js/nodejs/NodeJsRootExtension.kt
with(org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin.apply(rootProject)) {
    // canary nodejs that supports recent Wasm GC changes
    nodeVersion = "21.0.0-v8-canary202309167e82ab1fa2"
    nodeDownloadBaseUrl = "https://nodejs.org/download/v8-canary"
}
// Drop this when node js version become stable
tasks.withType(org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask::class).configureEach {
    args.add("--ignore-engines")
}
