/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal.publish

import ktorbuild.internal.kotlin
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

/**
 * Checks that direct dependencies are resolved to the declared versions.
 *
 * Gradle resolves version conflicts by picking the highest version, while Maven picks the "nearest" one.
 * If a module declares `C:1.0`, but one of its dependencies requires `C:1.1`, Gradle builds and tests
 * against `1.1`, but Maven consumers get the declared `1.0`, because a direct dependency is always the nearest.
 * The fix is to update the declared version to the resolved one.
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
        description = "Checks that declared dependencies are resolved to the declared versions, " +
            "so Maven consumers resolve the same versions as Gradle"
    }

    @TaskAction
    fun check() {
        val mismatches = rootComponent.get().dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            // Constraints end up in <dependencyManagement>, which Maven ignores in dependency POMs
            .filterNot { it.isConstraint }
            .mapNotNull { dependency ->
                val requested = dependency.requested as? ModuleComponentSelector ?: return@mapNotNull null
                // Skip dependencies substituted with a project or a non-module component
                val selected = dependency.selected.id as? ModuleComponentIdentifier ?: return@mapNotNull null
                if (requested.version.isEmpty() || requested.version == selected.version) return@mapNotNull null
                "${requested.moduleIdentifier}:${requested.version} -> ${selected.version}"
            }
            .distinct()
            .sorted()

        if (mismatches.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Declared dependencies of '${configurationName.get()}' resolved to different versions.")
                    appendLine("Maven consumers will get the declared versions. Update them to the resolved ones:")
                    mismatches.forEach { appendLine("  - $it") }
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
        // Look the target up instead of calling `kotlin.jvm()`, which would create it if it's missing
        val runtimeClasspath = kotlin.targets.withType<KotlinJvmTarget>().single().compilations
            .getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
            .runtimeDependencyConfigurationName

        configurationName = runtimeClasspath
        rootComponent = project.configurations.named(runtimeClasspath)
            .flatMap { it.incoming.resolutionResult.rootComponent }
    }
    tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(checkTask) }
}
