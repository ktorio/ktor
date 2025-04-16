/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal.publish

import ktorbuild.internal.gradle.maybeNamed
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.assign
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.File

/**
 * Task that verifies the list of published artifacts matches the expected artifacts.
 * Should be used together with exactly one publishing task.
 *
 * Examples:
 * ```
 * // Validate JS artifacts
 * ./gradlew validatePublishedArtifacts publishJsPublications
 *
 * // Save dump of JVM and common artifacts
 * ./gradlew validatePublishedArtifacts --dump publishJvmAndCommonPublications
 *
 * // Run validation against single project
 * ./gradlew validatePublishedArtifacts :ktor-io:publishJvmAndCommonPublications
 *
 * // Dump artifacts locally (switched publishing repository to MavenLocal)
 * ./gradlew validatePublishedArtifacts --dump publishJvmAndCommonPublications -Prepository=MavenLocal
 * ```
 *
 * The expected artifact list is stored in a [artifactsDump], and this task ensures
 * consistency between the expected and the actual artifacts published by the task.
 * `artifactsDump` for the task is located at `<rootProject>/gradle/artifacts/<taskName>.txt`.
 *
 * Initially cherry-picked from kotlinx.coroutines build scripts
 * See: https://github.com/Kotlin/kotlinx.serialization/blob/v1.8.0/buildSrc/src/main/kotlin/publishing-check-conventions.gradle.kts
 */
@Suppress("LeakingThis")
internal abstract class ValidatePublishedArtifactsTask : DefaultTask() {

    @get:Option(option = DUMP_OPTION, description = "Dumps the list of published artifacts to a file")
    @get:Input
    abstract val dump: Property<Boolean>

    @get:Input
    protected abstract val publishedArtifacts: ListProperty<String>

    @get:Internal
    protected abstract val publishTaskName: Property<String>

    @get:OutputFile
    protected abstract val artifactsDump: RegularFileProperty

    init {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Checks that the list of published artifacts matches the expected one"

        outputs.cacheIf("Isn't worth caching") { false }
        dump.convention(false)
        configurePublishTaskName()

        // Initialize the publishedArtifacts list to empty by default
        publishedArtifacts.convention(emptyList())

        // Collect artifacts from the all publishing tasks in the task graph
        project.gradle.taskGraph.whenReady {
            val publishTasks = allTasks.filterIsInstance<PublishToMavenRepository>()
            if (publishTasks.isNotEmpty()) {
                publishTasks.forEach { publishTask ->
                    publishedArtifacts.addAll(publishTask.publication.formatArtifacts())
                }
            }
        }
    }

    private fun configurePublishTaskName() {
        val taskNames = project.gradle.startParameter.taskNames.filterNot { name ->
            // Filter out the validation task itself and task parameters
            name == NAME || name.startsWith("-")
        }

        // Find publish tasks among the specified tasks
        val publishTasks = taskNames.filter { it.contains("publish", ignoreCase = true) }

        if (publishTasks.isEmpty()) {
            // If no publish task is specified, use a default name for the artifacts dump
            publishTaskName.set("defaultPublish")
            artifactsDump.set(project.rootDir.resolve("gradle/artifacts/defaultPublish.txt"))
            return
        }

        // Use the first publish task if multiple are specified
        publishTaskName.set(publishTasks.first())
        artifactsDump = publishTaskName.map { taskName ->
            val sanitizedTaskName = taskName.replace(":", "_")
            project.rootDir.resolve("gradle/artifacts/${sanitizedTaskName}.txt")
        }
    }

    fun dependsOn(publishedProjects: Provider<List<Project>>) = with(project) {
        // Handle the case when an absolute task path is specified
        val taskName = publishTaskName.get()
        if (taskName.startsWith(":")) {
            val publishTask = tasks.getByPath(taskName)
            dependsOn(publishTask)
            return
        }

        publishedProjects.get().forEach { project ->
            val task = project.tasks.maybeNamed(taskName)
            if (task != null) {
                dependsOn(task)
            }
        }
    }

    @TaskAction
    fun runValidation() {
        publishedArtifacts.finalizeValue()

        val actualArtifacts = publishedArtifacts.get().toSortedSet()
        val dumpFile = artifactsDump.get().asFile

        // If no artifacts were collected and we're not in dump mode, skip validation
        if (actualArtifacts.isEmpty() && !dump.get()) {
            logger.lifecycle("No artifacts were collected for validation. Skipping.")
            return
        }

        if (dump.get()) {
            if (!dumpFile.exists()) {
                dumpFile.parentFile.mkdirs()
                dumpFile.createNewFile()
            }

            dumpFile.bufferedWriter().use { writer ->
                actualArtifacts.forEach { artifact -> writer.appendLine(artifact) }
            }
            return
        }

        if (!dumpFile.exists()) reportDumpFileMissing(dumpFile)
        val expectedArtifacts = dumpFile.readLines().toSet()
        if (expectedArtifacts != actualArtifacts) reportInconsistentArtifacts(expectedArtifacts, actualArtifacts)
    }

    private fun reportDumpFileMissing(dumpFile: File): Nothing {
        throw GradleException("Expected artifacts dump file doesn't exist: $dumpFile\n$SOLUTION_DUMP")
    }

    private fun reportInconsistentArtifacts(expectedArtifacts: Set<String>, actualArtifacts: Set<String>): Nothing {
        val missingArtifacts = expectedArtifacts - actualArtifacts
        val extraArtifacts = actualArtifacts - expectedArtifacts
        val message = buildString {
            appendLine("The published artifacts differ from the expected ones")
            if (missingArtifacts.isNotEmpty()) {
                appendLine("  Expected artifacts are missing:")
                missingArtifacts.forEach { artifact -> appendLine("  - $artifact") }
            }
            if (extraArtifacts.isNotEmpty()) {
                appendLine("  Unexpected artifacts are found:")
                extraArtifacts.forEach { artifact -> appendLine("  - $artifact") }
            }
            appendLine(SOLUTION_DUMP)
        }
        throw GradleException(message)
    }

    companion object {
        const val NAME = "validatePublishedArtifacts"

        private const val DUMP_OPTION = "dump"
        private const val SOLUTION_DUMP = "To save current list of artifacts as expected, run '$NAME --$DUMP_OPTION'"
    }
}

private fun MavenPublication.formatArtifacts(): List<String> =
    artifacts.map { "${groupId}:${artifactId}/${it.classifier.orEmpty()}.${it.extension}" }
        .ifEmpty { listOf("$groupId:$artifactId") }
