/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild

import ktorbuild.ProjectTagsService.Companion.projectTagsService
import org.gradle.api.Project
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.registerIfAbsent

/**
 * Service allowing to aggregate projects by [ProjectTag] attached to it
 * in a way (potentially) compatible with "isolated projects" feature.
 *
 * Usage:
 * ```
 * // In some subproject
 * addProjectTag(ProjectTag.Library)
 *
 * // In a project aggregating libraries
 * val libraryProjects = projectsWithTag(ProjectTag.Library) // Provider<List<Project>>
 * ```
 *
 * @see addProjectTag
 * @see projectsWithTag
 */
abstract class ProjectTagsService : BuildService<BuildServiceParameters.None> {

    internal abstract val projectTags: MapProperty<String, Set<ProjectTag>>

    private val Project.tags: Set<ProjectTag>
        get() = projectTags.getting(path).orNull.orEmpty()

    internal fun addTag(project: Project, tag: ProjectTag) {
        projectTags.put(project.path, project.tags + tag)
    }

    internal fun hasTag(project: Project, tag: ProjectTag): Boolean = tag in project.tags

    internal fun getTagged(tag: ProjectTag): Set<String> {
        projectTags.finalizeValue()
        return projectTags.get().filterValues { tag in it }.keys
    }

    companion object {
        private const val NAME = "subprojectService"

        internal val Project.projectTagsService: ProjectTagsService
            get() = project.extensions.getByType<ProjectTagsService>()

        fun register(project: Project) {
            val service = project.gradle.sharedServices.registerIfAbsent(NAME, ProjectTagsService::class).get()
            project.extensions.add(NAME, service)
        }
    }
}

/** Adds the specified [tag] to this project. */
fun Project.addProjectTag(tag: ProjectTag) {
    projectTagsService.addTag(this, tag)
}

/**
 * Returns lazy property collecting list of projects marked with the specified [tag].
 *
 * Warning: Calling [Provider.get] evaluates all projects, so it should be done only when needed.
 * For example, if you need to add all projects with tag `Library` as "api" dependency to your project,
 * prefer lazy API to do so:
 *
 * ```
 * val libraryProjects = projectsWithTag(ProjectTag.Library)
 *
 * // Eager API (evaluates all project immediately)
 * dependencies {
 *     libraryProjects.get().forEach { api(it) }
 * }
 *
 * // Lazy API (evaluates all project only when 'api' configuration is needed)
 * configurations.api {
 *     dependencies.addAllLater(libraryProjects.mapValue { project.dependencies.create(it) })
 * }
 * ```
 *
 * Implicitly adds tag [ProjectTag.Meta] to this project.
 */
fun Project.projectsWithTag(tag: ProjectTag): Provider<List<Project>> = projectsWithTag(tag) { it }

/**
 * Returns lazy property collecting list of projects marked with the specified [tag] with [transform] applied to them.
 */
fun <T> Project.projectsWithTag(tag: ProjectTag, transform: (Project) -> T): Provider<List<T>> {
    addProjectTag(ProjectTag.Meta)

    // Postpone projects evaluation and tags freezing
    return provider {
        ensureAllProjectsEvaluated()
        projectTagsService.getTagged(tag).map { transform(project(it)) }
    }
}

private fun Project.ensureAllProjectsEvaluated() {
    val service = projectTagsService

    for (subproject in rootProject.subprojects) {
        if (subproject == this || service.hasTag(subproject, ProjectTag.Meta)) continue
        evaluationDependsOn(subproject.path)
    }
}

enum class ProjectTag {
    /** Project published to the Maven repository. */
    Published,

    /** Public library project. Implies [Published]. */
    Library,

    /** Project containing JVM target. Implies [Library]. */
    Jvm,

    /** Project aggregating information about other projects. */
    Meta,
}


