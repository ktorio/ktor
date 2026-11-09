/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal.publish

import ktorbuild.internal.kotlin
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

/**
 * Checks that direct dependencies are not upgraded transitively.
 *
 * Gradle resolves version conflicts by picking the highest version, while Maven picks the "nearest" one.
 * If a module declares `C:1.0`, but one of its dependencies requires `C:1.1`, Gradle builds and tests
 * against `1.1`, but Maven consumers get the declared `1.0`, because a direct dependency is always the nearest.
 * The fix is to bump the declared version to the resolved one.
 *
 * Only direct dependencies are checked, the same way as `maven-sympathy` does. Only the JVM runtime classpath
 * is checked because it contains all dependencies that end up in the published JVM POM.
 *
 * See:
 * - https://jakewharton.com/nonsensical-maven-is-still-a-gradle-problem/
 * - https://github.com/GradleUp/maven-sympathy
 */
@DisableCachingByDefault(because = "Isn't worth caching")
internal abstract class CheckMavenDependencyConvergenceTask : DefaultTask() {

    @get:Internal
    abstract val configurationName: Property<String>

    @get:Internal
    abstract val rootComponent: Property<ResolvedComponentResult>

    init {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Checks that declared dependencies are not upgraded transitively, " +
            "so Maven consumers resolve the same versions as Gradle"
    }

    @TaskAction
    fun check() {
        val upgrades = rootComponent.get().dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .mapNotNull { dependency ->
                val requested = dependency.requested as? ModuleComponentSelector ?: return@mapNotNull null
                val selectedVersion = dependency.selected.moduleVersion?.version
                if (requested.version.isEmpty() || requested.version == selectedVersion) return@mapNotNull null
                "${requested.moduleIdentifier}:${requested.version} -> $selectedVersion"
            }
            .distinct()
            .sorted()

        if (upgrades.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Declared dependencies of '${configurationName.get()}' were upgraded transitively.")
                    appendLine("Maven consumers will get the declared (older) versions. Bump them to the resolved ones:")
                    upgrades.forEach { appendLine("  - $it") }
                }
            )
        }
    }
}

/**
 * Registers [CheckMavenDependencyConvergenceTask] for the runtime classpath of the JVM `main` compilation
 * and attaches it to the `check` lifecycle task.
 */
internal fun Project.registerMavenDependencyConvergenceCheck() {
    val checkTask = tasks.register<CheckMavenDependencyConvergenceTask>("checkMavenDependencyConvergence") {

        val runtimeClasspath = kotlin.jvm().compilations
            .getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
            .runtimeDependencyConfigurationName

        configurationName = runtimeClasspath
        rootComponent = project.configurations.named(runtimeClasspath)
            .flatMap { it.incoming.resolutionResult.rootComponent }
    }
    tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(checkTask) }
}
