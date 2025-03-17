/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal.publish

import ktorbuild.internal.publish.TestRepository.locateTestRepository
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.configure
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.File

/**
 * Task that verifies the list of published artifacts matches the expected artifacts.
 *
 * The expected artifact list is stored in a [artifactsDump], and this task ensures
 * consistency between the expected and the actual artifacts found in the [repositoryDirectory].
 *
 * By default, `artifactsDump` is located at `<rootProject>/gradle/artifacts.txt` and [TestRepository] location is used
 * as a `repositoryDirectory`.
 *
 * Initially cherry-picked from kotlinx.coroutines build scripts
 * See: https://github.com/Kotlin/kotlinx.serialization/blob/v1.8.0/buildSrc/src/main/kotlin/publishing-check-conventions.gradle.kts
 */
@Suppress("LeakingThis")
internal abstract class ValidatePublishedArtifactsTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Option(option = DUMP_OPTION, description = "Dumps the list of published artifacts to a file")
    @get:Input
    abstract val dump: Property<Boolean>

    @get:OutputFile
    abstract val artifactsDump: RegularFileProperty

    init {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Checks that the list of published artifacts matches the expected one"

        outputs.cacheIf("Enable cache only when collecting a dump") { dump.get() }

        repositoryDirectory.convention(project.locateTestRepository())
        artifactsDump.convention(project.rootProject.layout.projectDirectory.file("gradle/artifacts.txt"))
        packageName.convention(project.rootProject.group.toString())
        dump.convention(false)
    }

    @TaskAction
    fun validate() {
        val packagePath = packageName.get().replace(".", "/")
        val actualArtifacts = repositoryDirectory.get().asFile
            .resolve(packagePath)
            .list()
            ?.toSortedSet()
            .orEmpty()
        val dumpFile = artifactsDump.get().asFile

        if (dump.get()) {
            if (!dumpFile.exists()) dumpFile.createNewFile()

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

internal object TestRepository {
    const val NAME = "test"

    fun Project.configureTestRepository() {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = NAME
                    setUrl(locateTestRepository())
                }
            }
        }
    }

    fun Project.locateTestRepository(): Provider<Directory> =
        rootProject.layout.buildDirectory.dir("${NAME}Repository")
}
