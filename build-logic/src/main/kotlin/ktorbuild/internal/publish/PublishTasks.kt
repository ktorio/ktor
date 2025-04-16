/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal.publish

import ktorbuild.internal.capitalized
import ktorbuild.targets.KtorTargets
import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.withType

private val jvmAndCommonPublications = setOf(
    "jvm",
    "androidRelease",
    "androidDebug",
    "metadata",
    "kotlinMultiplatform",
    "maven",
)

private val jsPublications = KtorTargets.resolveTargets("jsAndWasmShared")
private val linuxPublications = KtorTargets.resolveTargets("linux")
private val windowsPublications = KtorTargets.resolveTargets("windows")
private val darwinPublications = KtorTargets.resolveTargets("darwin")
private val androidNativePublications = KtorTargets.resolveTargets("androidNative")

internal fun AbstractPublishToMaven.isAvailableForPublication(publicationName: String, os: OperatingSystem): Boolean {
    return when (publicationName) {
        in linuxPublications -> os.isLinux
        in windowsPublications -> os.isWindows
        in darwinPublications -> os.isMacOsX
        in jvmAndCommonPublications,
        in jsPublications,
        in androidNativePublications -> true

        else -> {
            logger.warn("Unknown publication: $publicationName (project ${project.path})")
            false
        }
    }
}

internal fun Project.registerCommonPublishTask() {
    registerAggregatingPublishTask("JvmAndCommon", jvmAndCommonPublications)
}

/**
 * Configures aggregating publish tasks for various target groups, depending on the present targets
 * in the project.
 */
internal fun Project.registerTargetsPublishTasks(targets: KtorTargets) = with(targets) {
    if (hasJs || hasWasmJs) registerAggregatingPublishTask("Js", jsPublications)
    if (hasLinux) registerAggregatingPublishTask("Linux", linuxPublications)
    if (hasWindows) registerAggregatingPublishTask("Windows", windowsPublications)
    if (hasDarwin) registerAggregatingPublishTask("Darwin", darwinPublications)
    if (hasAndroidNative) registerAggregatingPublishTask("AndroidNative", androidNativePublications)
}

private fun Project.registerAggregatingPublishTask(name: String, publications: Set<String>) {
    tasks.register("publish${name}Publications") {
        group = PublishingPlugin.PUBLISH_TASK_GROUP

        val repositoryName = providers.gradleProperty("repository").orElse(MAVEN_REPO_DEFAULT_NAME)
        val taskNames = publications
            .map { "publish${it.capitalized()}PublicationTo${repositoryName.get().capitalized()}Repository" }
            .toSet()

        dependsOn(tasks.withType<PublishToMavenRepository>().named { it in taskNames })
    }
}

private const val MAVEN_REPO_DEFAULT_NAME = "maven"
