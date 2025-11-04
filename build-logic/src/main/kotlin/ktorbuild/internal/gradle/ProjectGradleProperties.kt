/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal.gradle

import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.*
import org.gradle.kotlin.dsl.of
import java.io.File
import java.util.*

/**
 * A utility class providing access to project properties declared in `gradle.properties`
 * files from the project directory up to the root project. The properties closer to the
 * current project directory take precedence over properties in parent directories.
 *
 * It is a workaround for the issue when `ProviderFactory.gradleProperty` and `GradleProperties`
 * take into account only root `gradle.properties`.
 * TODO: Remove when the issue is fixed
 *   https://github.com/gradle/gradle/issues/23572
 */
internal abstract class ProjectGradleProperties : ValueSource<Map<String, String>, ProjectGradleProperties.Parameters> {

    private val properties: Map<String, String> by lazy {
        val properties = mutableMapOf<String, String>()
        parameters.projectDirectory.get()
            .walkUpToRoot(parameters.rootDirectory.get())
            .map { it.resolve(Project.GRADLE_PROPERTIES) }
            .mapNotNull(File::loadProperties)
            // Properties closer to the current project take precedence
            .forEach(properties::putAllAbsent)

        properties
    }

    override fun obtain(): Map<String, String> {
        val prefix = parameters.prefix.orNull

        return if (prefix == null) {
            properties
        } else {
            properties.filterKeys { it.startsWith(prefix) }
                .mapKeys { (key, _) -> key.removePrefix(prefix) }
        }
    }

    interface Parameters : ValueSourceParameters {
        val prefix: Property<String>
        val projectDirectory: Property<File>
        val rootDirectory: Property<File>
    }
}

@Suppress("UnstableApiUsage")
internal fun ProviderFactory.projectGradleProperties(projectLayout: ProjectLayout, prefix: String): Provider<Map<String, String>> {
    return of(ProjectGradleProperties::class) {
        parameters {
            this.prefix.set(prefix)
            projectDirectory.set(projectLayout.projectDirectory.asFile)
            rootDirectory.set(projectLayout.settingsDirectory.asFile)
        }
    }
}

//region Utils
internal fun File.loadProperties(): Map<String, String>? {
    if (!exists()) return null
    return bufferedReader().use {
        @Suppress("UNCHECKED_CAST")
        Properties().apply { load(it) }.toMap() as Map<String, String>
    }
}

private fun File.walkUpToRoot(rootDir: File): Sequence<File> = sequence {
    var current = absoluteFile
    while(true) {
        yield(current)
        if (current == rootDir || current.parent == null) break
        current = current.parentFile
    }
}

private fun <K, V> MutableMap<K, V>.putAllAbsent(other: Map<K, V>) {
    for ((key, value) in other) {
        if (key !in this) this[key] = value
    }
}
//endregion
