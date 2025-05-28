/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UnstableApiUsage")

import org.gradle.api.attributes.DocsType.DOCS_TYPE_ATTRIBUTE
import org.gradle.api.attributes.DocsType.SOURCES
import org.gradle.kotlin.dsl.support.*

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.develocity)
    implementation(libs.develocity.commonCustomUserData)
}

// Should be synced with gradle/gradle-daemon-jvm.properties
kotlin {
    jvmToolchain(21)
}

//region Workaround for https://github.com/gradle/gradle/issues/13020
// We have a dependency on Kotlin Gradle Plugin and Gradle _always_ logs an annoying warning because of that:
//  Unsupported Kotlin plugin version.
// The warning is always logged, no matter what, and it doesn't ever seem to represent an actual problem.
// So, this code downloads the original EmbeddedKotlinPlugin and disables the warning.
// Because Gradle's classloader is hierarchical, the original class will be replaced.
// Copied from https://github.com/kotest/kotest/pull/4301

val kotlinDslPluginSources: Configuration by configurations.creating {
    description = "Download the original Gradle Kotlin DSL plugin source code."
    isCanBeConsumed = false
    isCanBeResolved = false
    isCanBeDeclared = true
    isVisible = false
    defaultDependencies {
        add(project.dependencies.create("org.gradle.kotlin:gradle-kotlin-dsl-plugins:$expectedKotlinDslPluginsVersion"))
    }
}

val kotlinDslPluginSourcesResolver: Configuration by configurations.creating {
    description = "Resolve files from ${kotlinDslPluginSources.name}."
    isCanBeConsumed = false
    isCanBeResolved = true
    isCanBeDeclared = false
    isVisible = false
    extendsFrom(kotlinDslPluginSources)
    attributes {
        attribute(DOCS_TYPE_ATTRIBUTE, objects.named(SOURCES))
    }
}

val suppressGradlePluginVersionWarning by tasks.registering {
    description = "Download EmbeddedKotlinPlugin.kt and patch it to disable the warning."

    val src = kotlinDslPluginSourcesResolver.incoming.files
    inputs.files(src).withNormalizer(ClasspathNormalizer::class)

    outputs.dir(temporaryDir).withPropertyName("outputDir")

    val archives = serviceOf<ArchiveOperations>()

    doLast {
        val embeddedKotlinPlugin = src.flatMap { s ->
            archives.zipTree(s).matching {
                include("**/EmbeddedKotlinPlugin.kt")
            }
        }.firstOrNull()

        if (embeddedKotlinPlugin == null) {
            // If EmbeddedKotlinPlugin.kt can't be found, then maybe this workaround
            // is no longer necessary, or it needs to be updated.
            logger.warn("[$path] Could not find EmbeddedKotlinPlugin.kt in $src")
        } else {
            logger.info("[$path] Patching EmbeddedKotlinPlugin.kt to remove 'Unsupported Kotlin plugin version' warning")
            temporaryDir.deleteRecursively()
            temporaryDir.mkdirs()
            temporaryDir.resolve(embeddedKotlinPlugin.name).apply {
                writeText(
                    embeddedKotlinPlugin.readText()
                        // This is the key change: converting 'warn' into 'info'.
                        .replace("\n        warn(\n", "\n        info(\n")
                        // Mark internal things as internal to prevent compiler warnings about unused code,
                        // and to stop them leaking into build scripts.
                        .replace("\n\nfun Logger.", "\n\nprivate fun Logger.")
                        .replace(
                            "*/\nabstract class EmbeddedKotlinPlugin",
                            "*/\ninternal abstract class EmbeddedKotlinPlugin"
                        )
                )
            }
        }
    }
}

sourceSets {
    main {
        kotlin.srcDir(suppressGradlePluginVersionWarning)
    }
}
//endregion
