/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.dokka.gradle.*
import org.jetbrains.kotlin.gradle.targets.js.nodejs.*
import org.jetbrains.kotlin.konan.target.*

val releaseVersion: String? by extra
val eapVersion: String? by extra
val projectVersion = project.version as String
val version = if (projectVersion.endsWith("-SNAPSHOT")) {
    projectVersion.dropLast("-SNAPSHOT".length)
} else {
    projectVersion
}

extra["configuredVersion"] = when {
    releaseVersion != null -> releaseVersion
    eapVersion != null -> "$version-eap-$eapVersion"
    else -> project.version
}

println("The build version is ${extra["configuredVersion"]}")

extra["globalM2"] = "$buildDir/m2"
extra["publishLocal"] = project.hasProperty("publishLocal")

val configuredVersion: String by extra

apply(from = "gradle/experimental.gradle")
apply(from = "gradle/verifier.gradle")

val experimentalAnnotations: List<String> by extra

/**
 * `darwin` is subset of `posix`.
 * Don't create `posix` and `darwin` sourceSets in single project.
 */
val platforms = mutableListOf("common", "jvm", "js", "posix", "darwin")

extra["skipPublish"] = mutableListOf<String>()
extra["nonDefaultProjectStructure"] = mutableListOf("ktor-bom")

fun projectNeedsPlatform(project: Project, platform: String): Boolean {
    val skipPublish: List<String> by rootProject.extra
    if (skipPublish.contains(project.name)) return platform == "jvm"

    val files = project.projectDir.listFiles() ?: emptyArray()
    val hasPosix = files.any { it.name == "posix" }
    val hasDarwin = files.any { it.name == "darwin" }

    if (hasPosix && hasDarwin) return false

    if (hasPosix && platform == "darwin") return false
    if (hasDarwin && platform == "posix") return false
    if (!hasPosix && !hasDarwin && platform == "darwin") return false

    return files.any { it.name == "common" || it.name == platform }
}

val disabledExplicitApiModeProjects = listOf(
    "ktor-client-tests",
    "ktor-client-json-tests",
    "ktor-server-test-host",
    "ktor-server-test-suites",
    "ktor-server-tests"
)

plugins {
    id("org.jetbrains.dokka") version "1.4.32"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.8.0"
    id("kotlinx-atomicfu") version "0.17.1" apply false
}

apply(from = "gradle/compatibility.gradle")

allprojects {
    group = "io.ktor"
    version = configuredVersion
    extra["hostManager"] = HostManager()

    repositories {
        mavenLocal()
        mavenCentral()
    }

    val nonDefaultProjectStructure: List<String> by rootProject.extra
    if (nonDefaultProjectStructure.contains(project.name)) return@allprojects

    apply(plugin = "kotlinx-atomicfu")
    apply(plugin = "kotlin-multiplatform")

    apply(from = rootProject.file("gradle/utility.gradle"))

    extra["nativeTargets"] = mutableListOf<String>()
    extra["nativeCompilations"] = mutableListOf<String>()

    platforms.forEach { platform ->
        val projectNeedsPlatform = projectNeedsPlatform(this, platform)

        if (projectNeedsPlatform) {
            if (platform == "js") {
                configureJsModules()
            } else {
                apply(from = rootProject.file("gradle/$platform.gradle"))
            }
        }
    }

    configurations {
        maybeCreate("testOutput")
    }

    kotlin {
        if (!disabledExplicitApiModeProjects.contains(project.name)) {
            explicitApi()
        }

        sourceSets.matching { !(it.name in listOf("main", "test")) }.all {
            val srcDir = if (name.endsWith("Main")) "src" else "test"
            val resourcesPrefix = if (name.endsWith("Test")) "test-" else ""
            val platform = name.dropLast(4)

            kotlin.srcDir("$platform/$srcDir")
            resources.srcDir("$platform/${resourcesPrefix}resources")

            languageSettings.apply {
                progressiveMode = true
                experimentalAnnotations.forEach { optIn(it) }

                if (project.path.startsWith(":ktor-server:ktor-server") && project.name != "ktor-server-core") {
                    optIn("io.ktor.server.engine.EngineAPI")
                }
            }
        }
    }

    val skipPublish: List<String> by rootProject.extra
    if (!skipPublish.contains(project.name)) {
        apply(from = rootProject.file("gradle/publish.gradle"))
    }
}

if (project.hasProperty("enableCodeStyle")) {
    apply(from = "gradle/codestyle.gradle")
}

println("Using Kotlin compiler version: ${org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION}")

subprojects {
    plugins.apply("org.jetbrains.dokka")

    tasks.withType<DokkaTaskPartial> {
        dokkaSourceSets.configureEach {
            if (platform.get().name == "js") {
                suppress.set(true)
            }
        }
    }
}

val docs: String? by extra
if (docs != null) {
    tasks.withType<DokkaMultiModuleTask> {
        pluginsMapConfiguration.set(mapOf("org.jetbrains.dokka.versioning.VersioningPlugin" to """{ "version": "$configuredVersion", "olderVersionsDir":"$docs" }"""))
    }
}

rootProject.plugins.withType<NodeJsRootPlugin> {
    rootProject.extensions.getByType<NodeJsRootExtension>().nodeVersion = "16.14.0"
}
