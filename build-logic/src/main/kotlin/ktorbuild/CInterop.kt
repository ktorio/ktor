/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild

import ktorbuild.targets.KtorTargets
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.withType
import org.jetbrains.dokka.gradle.tasks.DokkaGenerateModuleTask
import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultCInteropSettings
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

/**
 * Workaround for KT-76147: the commonizer accesses `Task.project` when the metadata compilation task executes.
 *
 * It doesn't fail when the task graph is loaded from the configuration cache, so it isn't noticeable in most builds.
 * It fails when the task graph isn't loaded from the cache, though: during IDE sync,
 * or when the cache entry is discarded because of another incompatible task or a configuration cache problem.
 *
 * TODO: Remove when Kotlin is updated to 2.5
 */
fun KotlinMultiplatformExtension.disableNativeCompileConfigurationCache() {
    // Disable configuration cache for compile[target]MainKotlinMetadata tasks
    project.tasks.withType<KotlinNativeCompile>()
        .named { it.endsWith("MainKotlinMetadata") }
        .configureEach { notCompatibleWithConfigurationCache("Workaround for KT-76147") }

    // The problem with compile*MainKotlinMetadata tasks also affects Dokka tasks
    project.tasks.withType<DokkaGeneratePublicationTask>()
        .named { it == "dokkaGeneratePublicationHtml" }
        .configureEach { notCompatibleWithConfigurationCache("Workaround for KT-76147") }
    project.tasks.withType<DokkaGenerateModuleTask>()
        .named { it == "dokkaGenerateModuleHtml" }
        .configureEach { notCompatibleWithConfigurationCache("Workaround for KT-76147") }
}

/**
 * Creates a CInterop configuration for all Native targets using the given [sourceSet]
 * in a Kotlin Multiplatform project.
 *
 * The [name] defines the CInterop configuration name. Definition file is expected to be located
 * at `[sourceSet]/interop/[name].def` by default, but can be customized via [definitionFilePath].
 * Additional configuration can be provided through [configure] block.
 *
 * Simple usage:
 * ```
 * kotlin {
 *     createCInterop("ssl", "posix")
 * }
 * ```
 *
 * Advanced usage with a separate definition for each target and additional configuration:
 * ```
 * kotlin {
 *     createCInterop(
 *         name = "ssl",
 *         sourceSet = "posix",
 *         definitionFilePath = { target -> "$target/interop/ssl.def" },
 *         configure = { target ->
 *             includeDirs("$target/interop/include")
 *             compilerOpts("-DUSE_SSL")
 *         }
 *     )
 * }
 * ```
 */
@Suppress("UnstableApiUsage")
fun KotlinMultiplatformExtension.createCInterop(
    name: String,
    sourceSet: String,
    definitionFilePath: (String) -> String = { "$sourceSet/interop/$name.def" },
    configure: DefaultCInteropSettings.(String) -> Unit = {}
) {
    val cinteropTargets = KtorTargets.resolveTargets(sourceSet)
    val projectDirectory = project.isolated.projectDirectory

    targets.named { it in cinteropTargets }
        .all {
            check(this is KotlinNativeTarget) { "Can't create cinterop for non-native target $targetName" }

            val main = compilations.getByName("main")
            main.cinterops.create(name) {
                definitionFile = projectDirectory.file(definitionFilePath(targetName))
                configure(targetName)
            }
        }

    disableNativeCompileConfigurationCache()
}
