/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal.gradle

import ktorbuild.targets.KtorTargets
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.*
import org.gradle.kotlin.dsl.of
import java.io.File

/** Provides a set of target directories available in the project. */
internal abstract class ProjectTargetDirectories : ValueSource<Set<String>, ProjectTargetDirectories.Parameters> {

    override fun obtain(): Set<String> {
        val knownSourceSets = KtorTargets.knownSourceSets
        return parameters.projectDirectory.get().walk()
            .maxDepth(1)
            .filter { it.isDirectory }
            .map { it.name }
            .filter { it in knownSourceSets }
            .toSet()
    }

    interface Parameters : ValueSourceParameters {
        val projectDirectory: Property<File>
    }
}

internal fun ProviderFactory.projectTargetDirectories(layout: ProjectLayout): Provider<Set<String>> {
    return of(ProjectTargetDirectories::class) { parameters.projectDirectory.set(layout.projectDirectory.asFile) }
}
